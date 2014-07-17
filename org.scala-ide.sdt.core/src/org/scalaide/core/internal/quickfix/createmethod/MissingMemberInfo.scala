package org.scalaide.core.internal.quickfix.createmethod

import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.scalariform.ArgPosition
import org.scalaide.util.internal.scalariform.MethodCallInfo
import org.scalaide.util.internal.scalariform.ScalariformUtils
import scala.tools.nsc.{interactive => compiler}
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector
import org.eclipse.jface.text.Position
import scalariform.parser.AstNode
import org.scalaide.core.internal.quickfix.ScalaQuickFixProcessor

class MissingMemberInfo(
    compilationUnit: ICompilationUnit,
    val fullyQualifiedName: String,
    val member: String,
    pos: Position,
    source: AstNode)
      extends HasLogger {

  val className = classNameFromFullyQualifiedName(fullyQualifiedName)
  lazy val targetElement: Option[IJavaElement] = targetElementFromCompiler.orElse(targetElementFromSearch)

  private def classNameFromFullyQualifiedName(theType: String) = theType.drop(theType.lastIndexOf('.') + 1)

  private def targetElementFromCompiler: Option[IJavaElement] = {
    val start = pos.offset
    val scu = compilationUnit.asInstanceOf[ScalaCompilationUnit]
    val allPossibleTargets = scu.withSourceFile { (srcFile, compiler) =>
      val membersInScope = new compiler.Response[List[compiler.Member]]
      val cpos = compiler.rangePos(srcFile, start, start, start)
      compiler.askScopeCompletion(cpos, membersInScope)

      for (members <- membersInScope.get.left.toOption) yield {
        compiler.askOption { () =>
          val elements = members.collect {
            case compiler.ScopeMember(sym, tpe, true, _) if !sym.isConstructor && sym.decodedName.equalsIgnoreCase( className) =>
              compiler.getJavaElement(tpe.typeSymbol, scu.getJavaProject).map(_.getParent)
          }
          elements.flatten.toSet
        }
      }
    }.flatten

    allPossibleTargets.flatten.getOrElse(Set()) match {
      case set if set.size == 1 => set.headOption
      case _ => None
    }
  }

  private def targetElementFromSearch: Option[IJavaElement] = {
    import ScalaQuickFixProcessor._

    logger.debug(s"Trying to search for $className to find the fully qualified class $fullyQualifiedName")
    val matchesClassName = searchForTypes(compilationUnit.getJavaProject, className)
    logger.debug(s"Result for className got results: ${matchesClassName}, ${matchesClassName.map(_.getFullyQualifiedName)}")

    val bestMatch = matchesClassName match {
      case oneResult :: Nil => Some(oneResult)
      case manyResults => manyResults.filter(_.getFullyQualifiedName().replaceAllLiterally("$.", ".") == fullyQualifiedName) match {
        case oneResult :: Nil => Some(oneResult)
        case _ => None
      }
    }
    logger.debug(s"Ended up with $bestMatch")
    bestMatch.map(_.getType.getCompilationUnit)
  }
}

object MissingMemberInfo {
  def inferFromEnclosingMethod(scu: ScalaCompilationUnit, source: AstNode, offset: Int): (ParameterList, ReturnType) = {
    def getParamsAndReturnType(offset: Int, length: Int, argPosition: ArgPosition): Option[(ParameterList, ReturnType)] = {
      val optopt = scu.withSourceFile { (srcFile, compiler) =>
        import compiler.{ log => _, _ }

        def getParameter(paramss: List[List[Symbol]]) = {
          argPosition.namedParameter match {
            case Some(paramName) => paramss(argPosition.argumentListIndex).find(param => {
              param.name.decoded == paramName
            })
            case None => Some(paramss(argPosition.argumentListIndex)(argPosition.argumentIndex))
          }
        }

        //isFunctionType taken from the compiler, Types.scala because I don't know how to access it here
        def isFunctionType(tp: Type): Boolean = tp.normalize match {
          case TypeRef(_, sym, args) if args.nonEmpty =>
            val arity = args.length - 1 // -1 is the return type
            arity <= definitions.MaxFunctionArity && sym == definitions.FunctionClass(arity)
          case _ =>
            false
        }

        val pos = compiler.rangePos(srcFile, offset, offset, offset + length)
        val typed = new compiler.Response[compiler.Tree]
        compiler.askTypeAt(pos, typed)
        val typedRes = typed.get
        compiler.askOption(() => {
          for {
            t <- typedRes.left.toOption
            tpe <- Option(t.tpe)
            parameter <- getParameter(tpe.paramss)
            paramType = parameter.tpe
            paramTypeRef = paramType.asInstanceOf[TypeRef]
          } yield {
            val (parameters, returnType) =
              if (isFunctionType(paramType)) {
                //we're passing our method in to a HoF, let's use the arguments of that FunctionN
                //as the arguments to our method, and the return type as ours
                //eg Function2[Int,String,Double] is (Int, String) => Double,
                //so we will make method(arg: Int, arg1: String): Double = { ??? }
                val args = paramTypeRef.args
                val parameters = List(args.init.map(arg => ("arg", arg.toString)))

                //when the return type is a type parameter, we don't want it, we'll return None
                //which will leave it off so we get method() = { ??? } which is fine
                val returnType = Some(args.last).filter(_.isTrivial) //not a type param

                (parameters, returnType.map(_.toString))
              } else {
                //not a HoF, so no parameters, just a return type
                (Nil, Some(paramTypeRef.sym.tpe.toString))
              }
            (parameters, returnType)
          }
        })
      }.flatten
      optopt.flatten.orElse(Some(Nil, None))
    }

    val result = for (MethodCallInfo(offset, length, argPosition) <- ScalariformUtils.callingOffsetAndLength(source, offset)) yield {
      getParamsAndReturnType(offset, length, argPosition)
    }
    result.flatten.getOrElse(Nil,None)
  }
}

package org.scalaide.core.internal.quickfix.abstractimpl

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.implementations.AddToClosest
import org.scalaide.core.internal.quickfix.createmethod.{ ParameterList, ReturnType }
import scala.tools.refactoring.implementations.AddMethodTarget
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.internal.eclipse.EditorUtils
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.internal.quickfix.AddMethodProposal

object ImplAbstractMembers {
  def suggestsFor(ssf: ScalaSourceFile, offset: Int): Array[IJavaCompletionProposal] =
    implAbstractMember(ssf, offset).toArray

  def implAbstractMember(ssf: ScalaSourceFile, offset: Int): List[IJavaCompletionProposal] = {
    ssf.withSourceFile { (srcFile, compiler) =>
      import compiler._

      type TypeParameterList = List[String]

      class AbstractMemberProposal(abstractMethod: MethodSymbol, implDef: ImplDef, protected val target: AddMethodTarget) extends AddMethodProposal {

        private def initValOrDef: (TypeParameterList, ParameterList, ReturnType, Boolean) = {
          def processType(tp: Type) =
            (if (tp.isError) None
            else if (tp.toString == "Null") Some("AnyRef")
            // TODO: add sprinter to remove redundant type prefix
            else Option(tp.toString())) getOrElse ("Any")

          val paramss: ParameterList = abstractMethod.paramss map {
            _.zipWithIndex.map { param =>
              // add implicit to first param if required
              ((if (param._1.isImplicit && (param._2 == 0)) s"${compiler.nme.IMPLICITkw} " else "") +
                  param._1.name.decode, processType(param._1.tpe.asSeenFrom(implDef.symbol.tpe, abstractMethod.owner)))
            }
          }
          val typeParams: TypeParameterList = abstractMethod.typeParams map (_.name.decode)
          val retType: ReturnType = Option(processType(abstractMethod.returnType.asSeenFrom(implDef.symbol.tpe, abstractMethod.owner)))
          val isDef = abstractMethod.isMethod && !abstractMethod.isAccessor
          (typeParams, paramss, retType, isDef)
        }

        protected val (typeParameters: TypeParameterList, parameters: ParameterList, returnType: ReturnType, isDef: Boolean) = initValOrDef
        val targetSourceFile = Option(ssf)
        val method = abstractMethod.nameString
        val className = Option(implDef.name.decode)

        override def getDisplayString(): String = {
          val (prettyParameterList, returnTypeStr) = getMethodInfo(parameters, returnType)
          val typeParametersList = if (!typeParameters.isEmpty) typeParameters.mkString("[",",","]") else ""
          s"Implement ${if (isDef) "def" else "val"} '${abstractMethod.nameString}$typeParametersList$prettyParameterList$returnTypeStr'"
        }
      }

      def implAbstractProposals(tree: ImplDef): List[IJavaCompletionProposal] =
        compiler.askOption { () =>
          val tp = tree.symbol.tpe
          (tp.members filter { m =>
            // TODO: find the way to get abstract methods simplier
            m.isMethod && m.isIncompleteIn(tree.symbol) && m.isDeferred && !m.isSetter && (m.owner != tree.symbol)
          } map {
            sym =>
              new AbstractMemberProposal(sym.asMethod, tree, AddToClosest(offset))
          }).toList
        } getOrElse Nil

      def createPosition(sf: SourceFile, offset: Int) =
        compiler.rangePos(srcFile, offset, offset, offset)

      def enclosingClassOrModule(src: SourceFile, offset: Int) =
        compiler.locateIn(compiler.parseTree(src), createPosition(src, offset),
          t => (t.isInstanceOf[ClassDef] || t.isInstanceOf[ModuleDef]))

      val enclosingTree = enclosingClassOrModule(srcFile, offset)
      if (enclosingTree != EmptyTree) {
        compiler.withResponse[Tree] { response =>
          compiler.askTypeAt(enclosingTree.pos, response)
        }.get.left.toOption flatMap {
          case implDef : ImplDef =>
            Option(implAbstractProposals(implDef))
          case _ => None
        } getOrElse (Nil)
      } else Nil
    } getOrElse (Nil)
  }
}

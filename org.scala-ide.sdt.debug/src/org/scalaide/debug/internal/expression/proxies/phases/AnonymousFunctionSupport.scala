/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import java.io.File

import scala.io.Source
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.NestedLambdaException
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.proxies.phases.AnonymousFunctionSupport.ClassListener
import org.scalaide.debug.internal.expression.proxies.phases.AnonymousFunctionSupport.NewClassContext
import org.scalaide.debug.internal.expression.FunctionProxyArgumentTypeNotInferredException

object AnonymousFunctionSupport {
  /** Pass this to toolbox as arguments to enable listening for new classes. */
  def toolboxOptions = s"-d $tempDir"

  case class NewClassContext(newClassName: String, newClassCode: Array[Byte])

  // tmp dir for compiled classes
  protected lazy val tempDir = {
    val tempFile = File.createTempFile("tmp_classes", "")
    tempFile.delete()
    tempFile.mkdirs()
    tempFile.getAbsolutePath
  }

  /** For locking */
  protected object ClassListenerLock

  /**
   * Listens for new classes created by `Toolbox.compile`
   * To work `AnonymousFunctionSupport.options` must be passed to toolbox that is used to set output for class files.
   *
   * @param className part of class name that should define required class
   */
  protected class ClassListener(className: String)(compile: () => Any) {
    private val parentDirFile = new File(AnonymousFunctionSupport.tempDir)

    private def findNewClassDirectory() = {
      val filesBeforeCompilation = parentDirFile.list().toSet
      compile()
      val filesAfterCompilation = parentDirFile.list().toSet

      (filesAfterCompilation diff filesBeforeCompilation).toSeq match {
        case Seq(newClassDir) => newClassDir
        case _ => throw new RuntimeException("Multiple package created for one compile method call")
      }
    }

    private def findNewClassFile(newClassDir: String) = {
      new File(parentDirFile, newClassDir).listFiles()
        .filter(_.getName.contains("$" + className)) match {
          case Array(classFile) => classFile
          case _ => throw NestedLambdaException
        }
    }

    private def newClassName(newClassDir: String, classFile: File) = {
      val generatedClassName = classFile.getName.replace(".class", "")
      s"$newClassDir.$generatedClassName"
    }

    private def newClassBytes(classFile: File) = {
      val codec = "ISO-8859-1"
      Source.fromFile(classFile, codec).map(_.toByte).toArray
    }

    /**
     * Listens for new class created during call of given function.
     * Check output dir of toolbox for new directory and search there class file contains given string.
     *
     * This method is synchronized across all instances as it creates files in filesystem.
     *
     * @param function function - during call there should be create new class
     * @return NewClassContext
     */
    def collectNewClassFiles: NewClassContext = ClassListenerLock.synchronized {
      val newClassDir = findNewClassDirectory()
      val requiredClassFile = findNewClassFile(newClassDir)

      NewClassContext(
        newClassName = newClassName(newClassDir, requiredClassFile),
        newClassCode = newClassBytes(requiredClassFile))
    }
  }
}

trait AnonymousFunctionSupport extends UnboundValuesSupport {

  import toolbox.u.{ Try => _, _ }

  /**
   * Unapply for placeholder function.
   * use it like case PlaceholderFunction(functionTypeName, functionReturnType, closureArguments)
   */
  protected object PlaceholderFunction {

    private def isPlaceholerFunction(fun: Tree): Boolean = {
      val treeString = fun.toString()
      treeString.startsWith(placeholderPartialFunctionString) ||
        treeString.startsWith(placeholderFunctionString)
    }

    private val placeholderFunctionString = {
      import Debugger._
      s"$contextFullName.$placeholderFunctionName"
    }

    private val placeholderPartialFunctionString = {
      import Debugger._
      s"$contextFullName.$placeholderPartialFunctionName"
    }

    def unapply(tree: Tree): Option[(String, String, List[Tree])] = tree match {
      case Apply(TypeApply(fun, List(proxyGenericType)), List(Literal(Constant(proxyType)), args)) if isPlaceholerFunction(fun) =>
        val closureArgs = args match {
          case TypeApply(Select(_, _), _) => Nil //default empty list
          case Apply(_, args) => args
        }
        Some((proxyType.toString, proxyGenericType.toString(), closureArgs))
      case _ => None
    }
  }

  //requirements
  protected val typesContext: TypesContext

  // for function naming
  private var functionsCount = 0

  // for new function name
  private val newClassName = "CustomFunction"

  private val ContextParamName = TermName(Debugger.contextParamName)
  // we should exlude start function -> it must stay function cos it is not a part of original expression
  protected def isStartFunctionForExpression(params: List[ValDef]) = params match {
    case List(ValDef(_, name, typeTree, _)) if name == ContextParamName => true
    case _ => false
  }

  /** Search for names that are from outside of given tree - must be closure parameters */
  protected final def getClosureParamsNames(body: Tree, vparams: List[ValDef]): Set[TermName] = {
    val vparamsName: Set[TermName] = vparams.map(_.name)(collection.breakOut)

    new VariableProxyTraverser(body).findUnboundNames() -- vparamsName
  }

  /** Search for names (with types) that are from outside of given tree - must be closure parameters */
  protected final def getClosureParamsNamesAndType(body: Tree, vparams: List[ValDef]): Map[TermName, String] = {
    val vparamsName: Set[TermName] = vparams.map(_.name)(collection.breakOut)
    new VariableProxyTraverser(body, typesContext.treeTypeName)
      .findUnboundValues().filterNot {
        case (name, _) => vparamsName.contains(name)
      }.map {
        case (name, Some(valueType)) => name -> valueType
        case (name, _) => name -> Debugger.proxyName
      }
  }

  /**
   * compiles a function
   * @param params list of function params (as Tree)
   * @param body body of function
   * @param closuresParams map of (closureParamName, closureArgumentType) for this function
   */
  protected def compileFunction(params: List[ValDef], body: Tree, closuresParams: Map[TermName, String]): NewClassContext = {
    val parametersTypes = params.map(_.tpt).flatMap(typesContext.treeTypeName)

    parametersTypes.foreach { elem =>
      if (elem == Debugger.proxyFullName) throw FunctionProxyArgumentTypeNotInferredException
    }

    val functionGenericTypes = (parametersTypes ++ Seq("Any")).mkString(", ")
    val constructorParams = closuresParams.map { case (name, paramType) => s"$name: $paramType" }.mkString(",")
    val arity = params.size
    val classCode =
      s"""class $newClassName($constructorParams) extends Function$arity[$functionGenericTypes]{
         |  override def apply(v1: Any) = ???
         |}""".stripMargin
    val newClass = toolbox.parse(classCode)

    val ClassDef(mods, name, tparams, Template(parents, self, impl)) = newClass

    val DefDef(functionMods, functionName, _, _, retType, _) = impl.last
    val newApplyFunction = DefDef(functionMods, functionName, Nil, List(params), retType, body)
    val newFunctionClass = ClassDef(mods, name, tparams, Template(parents, self, impl.dropRight(1) :+ newApplyFunction))
    val functionReseted = ResetTypeInformation(toolbox).transform(newFunctionClass)

    new ClassListener(newClassName)(() => {
      toolbox.compile(functionReseted)
    }).collectNewClassFiles
  }

  // creates and compiles new function class
  protected def createAndCompileNewFunction(params: List[ValDef], body: Tree, parentType: String): Tree = {
    val closuresParams = getClosureParamsNamesAndType(body, params)
    val NewClassContext(jvmClassName, classCode) = compileFunction(params, body, closuresParams)

    val proxyClassName = s"${parentType}v$functionsCount"
    functionsCount += 1
    val newFunctionType =
      typesContext.newType(proxyClassName, jvmClassName, parentType, classCode, closuresParams.values.toSeq)

    val closureParamTrees: List[Tree] = closuresParams.map { case (name, _) => Ident(name) }(collection.breakOut)

    lambdaProxy(newFunctionType, closureParamTrees)
  }

  /** creates proxy for given lambda with given closure arguments */
  protected def lambdaProxy(newFunctionType: String, closureArgs: List[Tree]): Tree = {
    val constructorArgs = Ident(TermName(Debugger.contextParamName)) :: closureArgs

    Apply(Select(Ident(TermName(newFunctionType)), TermName("apply")), constructorArgs)
  }

  /** Extract by name params for function as Seq of Boolean. This Seq might be shorter then param list - due to varargs */
  protected def extractByNameParams(select: Tree): Option[Seq[Boolean]] = Try {

    def innerArgs(tpe: Type): Seq[Boolean] =
      tpe match {
        case PolyType(_, realType) =>
          innerArgs(realType)
        case MethodType(params, resultType) =>
          params.map(isByNameParam)
        case _ => Nil
      }

    def isByNameParam(symbol: Symbol): Boolean = symbol match {
      case termSymbol: TermSymbol => termSymbol.isByNameParam
      case _ => false
    }

    innerArgs(select.tpe)
  }.toOption
}

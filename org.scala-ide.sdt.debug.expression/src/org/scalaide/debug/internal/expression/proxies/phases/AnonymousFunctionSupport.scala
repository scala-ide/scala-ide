/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import java.io.File

import scala.io.Source
import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Try

import Names.Debugger

object AnonymousFunctionSupport {
  /** Pass this to toolbox as arguments to enable listening for new classes. */
  def toolboxOptions = s"-d $tempDir"

  case class NewClassContext(newClassName: String, newClassCode: Array[Byte], nested: Seq[NewClassContext])

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
  protected class ClassListener(className: String, lambdaCode: String)(compile: () => Any) {
    private val parentDirFile = new File(AnonymousFunctionSupport.tempDir)

    private def findNewClassDirectory(): String = {
      val filesBeforeCompilation = parentDirFile.list().toSet
      try {
        compile()
      } catch {
        case t: Throwable => throw new LambdaCompilationFailure(lambdaCode, t)
      }
      val filesAfterCompilation = parentDirFile.list().toSet

      (filesAfterCompilation diff filesBeforeCompilation).toSeq match {
        case Seq(newClassDir) => newClassDir
        case _ => throw new RuntimeException("Multiple packages were created during single lambda compilation.")
      }
    }

    private def findNewClassFile(newClassDir: String): Seq[File] = {
      new File(parentDirFile, newClassDir).listFiles()
        .filter(_.getName.contains("$" + className)).sortBy(_.getName.size) //nested functions names are always longer
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
     * @return NewClassContext
     */
    def collectNewClassFiles: NewClassContext = ClassListenerLock.synchronized {
      val newClassDir = findNewClassDirectory()
      val requiredClassFile +: nested = findNewClassFile(newClassDir)

      def contextFromFile(file: File, nested: Seq[NewClassContext]) =
        NewClassContext(
          newClassName = newClassName(newClassDir, file),
          newClassCode = newClassBytes(file), nested)

      contextFromFile(requiredClassFile, nested.map(file => contextFromFile(file, Nil)))
    }
  }

}

/**
 * Contains data about new classes to load on debugged JVM.
 * Populated during lambda-related phases (mostly from `AnonymousFunctionSupport`).
 *
 * WARNING - this class have mutable (append-only) internal state
 */
final class NewTypesContext() {

  /** Maps function names to it's stubs */
  private var _newCodeClasses: Set[ClassData] = Set.empty

  /** Classes to be loaded on debugged jvm. */
  def classesToLoad: Iterable[ClassData] = _newCodeClasses

  /**
   * Creates a new type that will be loaded in debugged jvm.
   *
   * @param className name of class in jvm - must be same as in compiled code
   * @param jvmCode code of compiled class
   * @param constructorArgsTypes
   */
  def createNewType(className: String,
    jvmCode: Array[Byte],
    constructorArgsTypes: Seq[String]): Unit =
    _newCodeClasses += ClassData(className, jvmCode, constructorArgsTypes)
}

trait AnonymousFunctionSupport[+Tpe <: TypecheckRelation]
  extends AstTransformer[Tpe]
  with UnboundValuesSupport {

  val toolbox: ToolBox[universe.type]

  protected val typesContext = new NewTypesContext()

  override def transform(data: TransformationPhaseData): TransformationPhaseData =
    super.transform(data).withClasses(typesContext.classesToLoad.toSeq)

  import toolbox.u.{ Try => _, _ }

  import AnonymousFunctionSupport.ClassListener
  import AnonymousFunctionSupport.NewClassContext

  /**
   * Unapply for placeholder function.
   * use it like case PlaceholderFunction(functionTypeName, functionReturnType, closureArguments)
   */
  protected object PlaceholderFunction {

    private object LambdaType {
      def unapply(on: Tree): Option[String] = on match {
        case Literal(Constant(proxyType)) => Some(proxyType.toString)
        case Apply(_, Seq(Apply(_, Seq(Literal(Constant(proxyType)))))) => Some(proxyType.toString)
        case _ => None
      }
    }

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
      case Apply(TypeApply(fun, List(proxyGenericType)), List(LambdaType(proxyType), args)) if isPlaceholerFunction(fun) =>
        val closureArgs = args match {
          case TypeApply(Select(_, _), _) => Nil //default empty list
          case Apply(_, args) => args
        }
        Some((proxyType.toString, proxyGenericType.toString(), closureArgs))
      case _ => None
    }
  }

  private val ContextParamName = TermName(Debugger.contextParamName)

  // we should exclude start function -> it must stay function cos it is not a part of original expression
  protected def isStartFunctionForExpression(params: List[ValDef]) = params match {
    case List(ValDef(_, name, typeTree, _)) if name == ContextParamName => true
    case _ => false
  }

  /** Search for names that are from outside of given tree - must be closure parameters */
  protected final def getClosureParamsNames(body: Tree, vparams: List[ValDef]): Set[TermName] = {
    val vparamsName: Set[TermName] = vparams.map(_.name)(collection.breakOut)

    new VariableProxyTraverser(body).findUnboundVariables().map(_.name) -- vparamsName
  }

  /** Search for names (with types) that are from outside of given tree - must be closure parameters */
  protected final def getClosureParamsNamesAndType(body: Tree, vparams: List[ValDef]): Map[TermName, String] = {
    val vparamsName: Set[TermName] = vparams.map(_.name)(collection.breakOut)
    new VariableProxyTraverser(body, tree => TypeNames.fromTree(tree))
      .findUnboundValues().filterNot {
      case (variable, _) => vparamsName.contains(variable.name)
    }.flatMap {
      case (variable, Some(valueType)) =>
        val isFunctionImport = valueType.contains("$$")
        val fixedType = if (isFunctionImport) valueType else valueType.replace("$", ".")
        Some(variable.name -> fixedType)
      case _ => None
    }
  }

  /**
   * compiles a function
   * @param params list of function params (as Tree)
   * @param body body of function
   * @param closuresParams map of (closureParamName, closureArgumentType) for this function
   */
  protected def compileFunction(params: List[ValDef], body: Tree, closuresParams: Map[TermName, String]): NewClassContext = {
    val parametersTypes = params.map(v => TypeNames.getFromTree(v.tpt))

    parametersTypes.foreach { elem =>
      if (elem == Debugger.proxyFullName) throw FunctionProxyArgumentTypeNotInferredException
    }

    val functionGenericTypes = (parametersTypes ++ Seq("Any")).mkString(", ")
    val constructorParams = closuresParams.map { case (name, paramType) => s"$name: $paramType" }.mkString(",")
    val arity = params.size

    val lambdaParams = params.zip(parametersTypes).map{
      case (ValDef(mods, name, _, impl), typeName) =>
        s"$name: $typeName"
    }.mkString(", ")

    import Debugger.newClassName
    val classCode =
      s"""class $newClassName($constructorParams) extends Function$arity[$functionGenericTypes]{
         |  override def apply($lambdaParams) = ???
         |}""".stripMargin
    val newClass = toolbox.parse(classCode)

    val ClassDef(mods, name, tparams, Template(parents, self, impl)) = newClass

    val DefDef(functionMods, functionName, _, applyParams, retType, _) = impl.last
    val newApplyFunction = DefDef(functionMods, functionName, Nil, applyParams, retType, body)
    val newFunctionClass = ClassDef(mods, name, tparams, Template(parents, self, impl.dropRight(1) :+ newApplyFunction))
    val functionReseted = new ResetTypeInformation().transform(TransformationPhaseData(newFunctionClass)).tree

    new ClassListener(newClassName, functionReseted.toString)(() => {
      toolbox.compile(functionReseted)
    }).collectNewClassFiles
  }

  // creates and compiles new function class
  protected def createAndCompileNewFunction(params: List[ValDef], body: Tree): Tree = {
    val closuresParams = getClosureParamsNamesAndType(body, params)
    val NewClassContext(jvmClassName, classCode, nested) = compileFunction(params, body, closuresParams)

    nested.foreach(nestedFunction =>
      typesContext.createNewType(nestedFunction.newClassName, nestedFunction.newClassCode, Nil))

    typesContext.createNewType(jvmClassName, classCode, closuresParams.values.toSeq)

    val closureParamTrees: List[Tree] = closuresParams.map { case (name, _) => Ident(name) }(collection.breakOut)

    lambdaProxy(jvmClassName, closureParamTrees)
  }

  /** creates proxy for given lambda with given closure arguments */
  protected def lambdaProxy(newFunctionType: String, closureArgs: List[Tree]): Tree = {
    val constructorArgs =
      Literal(Constant(newFunctionType)) :: Apply(SelectApplyMethod("Seq"), closureArgs) :: Nil

    Apply(Select(Ident(TermName(Debugger.contextParamName)), TermName(Debugger.newInstance)), constructorArgs)
  }

  /**
   * Extracts by-name params for function as `Seq[Boolean]`.
   * This Seq might be shorter then param list - due to varargs.
   */
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

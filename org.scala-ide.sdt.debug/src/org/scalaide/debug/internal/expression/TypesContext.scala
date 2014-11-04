/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.TypeRef

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.Function0JdiProxy
import org.scalaide.debug.internal.expression.proxies.FunctionJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

import Names.Debugger
import Names.Java
import Names.Scala

/**
 * Represents new class to be loaded on remote .
 *
 * @param className name of class
 * @param parentClassName name of parent class
 * @param code array of bytes with class contents
 * @param constructorArgsTypes
 */
case class ClassData(className: String, parentClassName: String, code: Array[Byte], constructorArgsTypes: Seq[String])

/**
 * Represents mutable (append-only) state of [[org.scalaide.debug.internal.expression.TypesContext]].
 */
final class TypesContextState() {
  import universe.TermName

  /** Maps function names to it's stubs */
  private var _stubs: Map[String, Set[MethodStub]] = Map.empty

  /** Maps function names to it's stubs */
  private var _newCodeClasses: Map[String, ClassData] = Map.empty

  /** Holds all unbound variables */
  private var _unboundVariables: Set[TermName] = Set.empty

  /** Gets all types with function that are called   */
  def stubs: Map[String, Set[MethodStub]] = _stubs

  /** Gets all newly generated class */
  def newCodeClasses: Map[String, ClassData] = _newCodeClasses

  /** Gets all unbound variables */
  def unboundVariables: Set[TermName] = _unboundVariables

  /** Adds new stub to type state */
  def addStub(typeName: String, functions: Set[MethodStub]): Unit = _stubs += typeName -> functions

  /** Adds new class to type state */
  def addNewClass(name: String, data: ClassData): Unit = _newCodeClasses += name -> data

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[TermName]): Unit = _unboundVariables ++= variables
}

/**
 * Contains all information of types obtained during compilation of expression
 * During phrases it is filled with types and function that is called on that types.
 * It generates mapping for names - plain jvm names are translated to it proxy versions.
 * During the GenerateStub phase type context is used to create stub classes.
 *
 * WARNING - this class have mutable internal state
 */
final class TypesContext() {

  private val state: TypesContextState = new TypesContextState()

  /** Classes to be loaded on debugged jvm. */
  def classesToLoad: Iterable[ClassData] = state.newCodeClasses.values

  /** Function stubs */
  def stubs: Map[String, Set[MethodStub]] = state.stubs

  /** Gets all unbound variables */
  def unboundVariables: Set[universe.TermName] = state.unboundVariables

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[universe.TermName]): Unit = state.addUnboundVariables(variables)

  /**
   * Obtains type name as string for given tree.
   * All generics are cut off: `collection.immutable.List[Int]` becomes `collection.immutable.List`.
   * Fixes problems with `immutable.this.Nil` and `immutable.this.List`
   * Marks objects with special prefix.
   *
   * @param tree tree to search for
   */
  def treeTypeName(tree: universe.Tree): Option[String] = {
    if (tree.toString == Scala.thisNil) Some(Scala.nil)
    else typeName(tree.tpe, Option(tree.symbol).exists(_.isModule))
  }

  /** New type name for given type */
  final def treeStubType(tree: universe.Tree): Option[String] =
    treeTypeName(tree).map(stubType)

  /**
   * Returns valid stub type name from context.
   *
   * If not present it is added.
   * If not stubbable, `typeName` is returned.
   *
   * @param typeName name to get stub for
   */
  final def stubType(typeName: String): String = {
    getProxyForType(typeName).getOrElse(
      if (couldBeStubbed(typeName)) {
        state.addStub(typeName, getMethodsForType(typeName))
        stubName(typeName)
      } else typeName)
  }

  /**
   * Transforms given type name to proxy type name.
   * Works on nonstubbable types (e.g. primitives).
   * If type is stubbable adds this type to context.
   *
   * @param typeName name to get proxy type for
   */
  def proxyTypeFor(typeName: String): String =
    FunctionJdiProxy.functionToProxy(typeName) orElse
      BoxedJdiProxy.primitiveToProxy(typeName) getOrElse
      stubType(typeName)

  /** Type of proxy for by-name function parameter (that is, Function0JdiProxy) */
  val byNameType: String = FunctionJdiProxy.functionToProxy(Names.Scala.functions.Function0).get

  /**
   * Creates a new type that will be loaded in debugged jvm.
   *
   * @param proxyType type of proxy for new type e.g. Function2JdiProxy
   * @param className name of class in jvm - must be same as in compiled code
   * @param jvmCode code of compiled class
   * @param constructorArgsTypes
   * @return new type jdi name
   */
  def newType(proxyType: String,
    className: String,
    parentName: String,
    jvmCode: Array[Byte],
    constructorArgsTypes: Seq[String]): String = {
    val name =
      if (couldBeStubbed(proxyType)) stubType(proxyType)
      else throw new RuntimeException("Cannot create new class in jvm for not stubbable code!")

    state.addNewClass(proxyType, ClassData(className, parentName, jvmCode, constructorArgsTypes))
    name
  }

  /** Generate code for all stubs in context */
  def typesStubCode(): String =
    stubs.keys
      .filter(couldBeStubbed)
      .map(generateSingleStub)
      .mkString("\n")

  /**
   * Called for each new function found for given type
   * @param typeString type name
   * @param method method stub data
   */
  def newMethod(typeString: String, method: MethodStub) {
    if (shouldBeStubbed(method)) {
      val methods = getMethodsForType(typeString)
      state.addStub(typeString, methods + method)

      method.allTypes
        .filter(couldBeStubbed)
        .foreach(stubType)
    }
  }

  /**
   * Create java name for classes (replace . with $ for nested classes)
   */
  def jvmTypeForClass(tpe: universe.Type): String = {
    import universe.TypeRefTag
    // hack for typecheck replacing `List.apply()` with `immutable.this.Nil`
    if (tpe.toString == "List[Nothing]") Scala.nil
    else tpe.typeConstructor match {
      case universe.TypeRef(prefix, sym, _) if !prefix.typeConstructor.typeSymbol.isPackage =>
        val className = sym.name
        val parentName = jvmTypeForClass(prefix)
        parentName + "$" + className
      case _ =>
        tpe.typeSymbol.fullName
    }
  }

  /**
   * See `TypesContext.treeTypeName`.
   *
   * @param tpe type to search for name
   * @param isObject if given tree represents an object
   */
  private def typeName(tpe: universe.Type, isObject: Boolean): Option[String] = {
    def correctTypes(oldName: String): Option[String] = oldName match {
      case Scala.thisList => Some(Scala.list)
      case Scala.thisNil => Some(Scala.nil)
      case name if isObject => Some(JdiContext.toObjectOrStaticCall(name))
      case Scala.nothingType => None
      case name => Some(name)
    }

    rawType(tpe).flatMap(correctTypes)
  }

  private def rawType(tpe: universe.Type): Option[String] = {
    import universe._
    tpe match {
      case null => None
      case nullType if nullType.typeSymbol == null => None
      case AstMatchers.ArrayRef(typeParam) => Some(Scala.Array(typeParam.toString))
      case typeRef: TypeRef => Some(typeRef.typeSymbol.fullName)
      case singleType: SingleType => singleType.sym match {
        // for non-erased array types
        case methodSymbol: universe.MethodSymbol if methodSymbol.returnType.toString.startsWith("Array[") =>
          Some(methodSymbol.returnType.toString)
        case other => Some(singleType.typeSymbol.fullName)
      }
      case constantType: ConstantType => Some(constantType.typeSymbol.fullName)
      case thisType: ThisType => Some(thisType.typeSymbol.fullName)
      case thisType: ExistentialType => Some(thisType.typeSymbol.fullName)
      case method: MethodType => typeName(method.resultType, isObject = method.resultType.typeSymbol.isModule)
      case noneType if tpe.typeSymbol.toString == Scala.emptyType => Some(Scala.simpleNothingType)
      case polyType: PolyType => rawType(polyType.erasure)
      case wildcard if wildcard.toString == Scala.wildcardType => Some(Scala.simpleNothingType)
      case any => throw new RuntimeException(s"Unsupported tree shape: $any.")
    }
  }

  /** Generates the stub name for a given type - if it's not a special case just replace . with _ on full name */
  private def stubName(orginalType: String): String = orginalType match {
    case Debugger.proxyFullName | Debugger.proxyName => Debugger.proxyName
    case Debugger.contextFullName | Debugger.contextName => Debugger.contextName
    case other => other.replace(".", "_").replace("$$", "$_$")
  }

  private def getProxyForType(typeName: String): Option[String] = typeName match {
    case Scala.Array(typeParam) =>
      val typeParamProxy = getProxyForType(typeParam).getOrElse(Debugger.proxyName)
      Some(Debugger.ArrayJdiProxy(typeParamProxy))
    case _ => FunctionJdiProxy.functionToProxy(typeName) orElse BoxedJdiProxy.primitiveToProxy(typeName)
  }

  /** return all function for given type */
  private def getMethodsForType(name: String): Set[MethodStub] = stubs.get(name).getOrElse(Set())

  /** Excludes functions that should not be stubbed */
  private def shouldBeStubbed(function: MethodStub): Boolean = function match {
    case MethodStub("hashCode", _, _, Seq(Seq())) => false
    case MethodStub("toString", _, Some("java.lang.String"), Seq(Seq())) => false
    case MethodStub(Scala.constructorMethodName | Scala.notEqualsMethodName | Scala.equalsMethodName, _, _, _) => false

    case _ => true
  }

  private val stubCodeGenerator = new StubCodeGenerator(this)

  import Names.Debugger._

  /** Generates code for an existing class (originally loaded in JVM) */
  private def generateStubForExistingClass(name: String): String = {
    val methods = stubs(name).map(stubCodeGenerator.apply).mkString("\n\t")
    s"""
        |case class ${stubType(name)}(private val $proxyContextName: JdiProxy)
        |    extends JdiProxyWrapper($proxyContextName) {
        |  $methods
        |}
        """.stripMargin
  }

  /**  Generate stub for class that will be loaded in JVM - generated for this expression */
  private def generateStubForNewClass(name: String): String = {
    val data = state.newCodeClasses(name)
    val proxyParentClass = FunctionJdiProxy.functionToProxy(data.parentClassName).getOrElse(s"Not function: $name")
    val className = stubType(name)
    val realClassName = data.className
    val argsCode =
      if (data.constructorArgsTypes.isEmpty) ""
      else data.constructorArgsTypes.zipWithIndex.map {
        case (paramType, index) =>
          val stubType = proxyTypeFor(paramType)
          s"v$index: $stubType"
      }.mkString(", ", ", ", "")
    val constructorArgumentsSeq = (0 to (data.constructorArgsTypes.size - 1)).map("v" + _).mkString(", ")
    s"""case class $className(protected val $newClassContextName: JdiContext $argsCode) extends $proxyParentClass {
       | override val className = "$realClassName"
       | override val constructorArguments = Seq(Seq($constructorArgumentsSeq))
       |}""".stripMargin
  }

  /** Generate stub for single type */
  private def generateSingleStub(name: String): String = {
    val isNewClass = state.newCodeClasses.contains(name)
    if (isNewClass) generateStubForNewClass(name)
    else generateStubForExistingClass(name)
  }

  /** All notstubbable classes */
  private val notStubbable = Set(
    Scala.nothingType,
    Scala.unitType,
    Scala.nullType,

    Java.boxed.String,

    //JdiProxy in all forms
    Debugger.proxyName,
    Debugger.proxyFullName,
    Debugger.proxyObjectOrStaticCallFullName,

    //JdiContext in all forms
    Debugger.contextName,
    Debugger.contextFullName,
    Debugger.contextObjectOrStaticCallFullName) ++
    FunctionJdiProxy.functionNames ++
    FunctionJdiProxy.functionProxyNames

  /** Could class with given name be stubbed. */
  private def couldBeStubbed(stub: String): Boolean = {
    def isPrimitive(name: String) = Scala.primitives.all.contains(name)
    def isArray(name: String) = Scala.Array.pattern.matcher(name).matches
    !(notStubbable.contains(stub) || isPrimitive(stub) || isArray(stub))
  }
}
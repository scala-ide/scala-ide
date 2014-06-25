/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.FunctionJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

/**
 * Represents new class to be loaded on remote .
 *
 * @param className name of class
 * @param parentClassName name of parent class
 * @parma code array of bytes with class contents
 */
case class ClassData(className: String, parentClassName: String, code: Array[Byte])

/**
 * Represents mutable (append-only) state of [[org.scalaide.debug.internal.expression.TypesContext]].
 */
final class TypesContextState() {

  /** Maps function names to it's stubs */
  private var _stubs: Map[String, Set[FunctionStub]] = Map.empty

  /** Maps function names to it's stubs */
  private var _newCodeClasses: Map[String, ClassData] = Map.empty

  /** Holds all unbound variables */
  private var _unboundVariables: Set[String] = Set.empty

  /** Gets all types with function that are called   */
  def stubs: Map[String, Set[FunctionStub]] = _stubs

  /** Gets all newly generated class */
  def newCodeClasses: Map[String, ClassData] = _newCodeClasses

  /** Gets all unbound variables */
  def unboundVariables: Set[String] = _unboundVariables

  /** Adds new stub to type state */
  def addStub(typeName: String, functions: Set[FunctionStub]): Unit = _stubs += typeName -> functions

  /** Adds new class to type state */
  def addNewClass(name: String, data: ClassData): Unit = _newCodeClasses += name -> data

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[String]): Unit = _unboundVariables ++= variables
}

/**
 * Contains all information of types obtained during compilation of expression
 * During phrases it is filled with types and function that is called on that types.
 * It generates mapping for names - plain jvm names are translated to it proxy versions.
 * During the GenerateStub pharse type context is used to create stub classes.
 *
 * WARNING - this class have mutable internal state
 */
final class TypesContext() {

  private val state: TypesContextState = new TypesContextState()

  /** Classes to be loaded on debuged jvm. */
  def classesToLoad: Iterable[ClassData] = state.newCodeClasses.values

  /** Function stubs */
  def stubs: Map[String, Set[FunctionStub]] = state.stubs

  /** Gets all unbound variables */
  def unboundVariables: Set[String] = state.unboundVariables

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[String]): Unit = state.addUnboundVariables(variables)

  /**
   * Obtains type name for given tree
   * @param tree tree to search for
   */
  def treeTypeName(tree: universe.Tree): Option[String] = {
    if (tree.toString == ScalaOther.thisNil) Some(ScalaOther.nil)
    else typeName(tree.tpe, Option(tree.symbol).map(_.isModule).getOrElse(false))
  }

  /** New type name for given type */
  def treeTypeFromContext(tree: universe.Tree): Option[String] =
    treeTypeName(tree).map(typeFromContext)

  /**
   * Get valid type name from context - if not present it is added
   * if type name is valid it it returned as it is
   */
  def typeNameFor(name: String): String = typeFromContext(name)

  /** Type of proxy for by-name function paramater (that is, Function0JdiProxy) */
  val byNameType: String = FunctionJdiProxy.functionToProxyMap(ScalaFunctions.Function0)

  /**
   * Creates a new type that will be loaded in debugged jvm.
   *
   * @param proxyType type of proxy for new type eg. Function2JdiProxy
   * @param className name of class in jvm - must be same as in compiled code
   * @param jvmCode code of compiled class
   * @return new type jdi name
   */
  def newType(proxyType: String, className: String, parentName: String, jvmCode: Array[Byte]): String = {
    val name =
      if (couldBeStubbed(proxyType)) typeFromContext(proxyType)
      else throw new RuntimeException("Cannot create new class in jvm for not stubbable code!")

    state.addNewClass(proxyType, ClassData(className, parentName, jvmCode))
    name
  }

  /** Generate code for all stubs in context */
  def typesStubCode: String =
    stubs.keys
      .filter(couldBeStubbed)
      .map(generateSingleStub)
      .mkString("\n")

  /**
   * Called for each new function found for given type
   * @param typeString type name
   * @param function function stub data
   */
  def newFunction(typeString: String, function: FunctionStub) {
    if (shouldBeStubbed(function)) {
      val functions = getFunctionsForType(typeString)
      state.addStub(typeString, functions + function)

      function.allTypes
        .filter(couldBeStubbed)
        .foreach(typeFromContext)
    }
  }

  /**
   * TODO - Krzysiek - document this
   */
  def jvmTypeForClass(tpe: universe.Type): String = {
    import universe.TypeRefTag
    tpe.typeConstructor match {
      case universe.TypeRef(parent, sym, _) if !parent.typeConstructor.typeSymbol.isPackage =>
        val className = sym.name
        val parentName = jvmTypeForClass(parent)
        parentName + "$" + className
      case _ =>
        tpe.typeSymbol.fullName
    }
  }

  /**
   * Obtains type name for given tree.
   *
   * @param tree tree to search for
   * @param isObject if given tree represents an object
   */
  private def typeName(tpe: universe.Type, isObject: Boolean): Option[String] = {
    def correctTypes(oldName: String): Option[String] = oldName match {
      case ScalaOther.thisList => Some(ScalaOther.list)
      case ScalaOther.thisNil => Some(ScalaOther.nil)
      case any if isObject => Some(JdiContext.toObject(any))
      case ScalaOther.nothingType => None
      case any => Some(any)
    }

    rawType(tpe).flatMap(correctTypes)
  }

  private def rawType(tpe: universe.Type): Option[String] = {
    import universe._
    tpe match {
      case typeRef: TypeRef => Some(typeRef.typeSymbol.fullName)
      case singleType: SingleType => Some(singleType.typeSymbol.fullName)
      case constantType: ConstantType => Some(constantType.typeSymbol.fullName)
      case thisType: ThisType => Some(thisType.typeSymbol.fullName)
      case method: MethodType => typeName(method.resultType, isObject = method.resultType.typeSymbol.isModule)
      case noneType if tpe.typeSymbol.toString == ScalaOther.emptyType => Some(ScalaOther.simpleNothingType)
      case polyType: PolyType => rawType(polyType.erasure)
      case wildcard if wildcard.toString == ScalaOther.wildcardType => Some(ScalaOther.simpleNothingType)
      case any => throw new RuntimeException(s"Unsupported tree shape: $any.")
    }
  }

  /** Genereate stub name for given type - if not a special case just replace . with _ on full name */
  private def stubName(orginalType: String): String = orginalType match {
    case DebuggerSpecific.proxyFullName => DebuggerSpecific.proxyName
    case DebuggerSpecific.contextFullName => DebuggerSpecific.contextName
    case DebuggerSpecific.proxyName => DebuggerSpecific.proxyName
    case DebuggerSpecific.contextName => DebuggerSpecific.contextName
    case other => other.replace(".", "_")
  }

  private def getProxyForType(typeName: String): Option[String] = typeName match {
    case "scala.Array" => Some(classOf[ArrayJdiProxy].getSimpleName)
    case _ => (FunctionJdiProxy.functionToProxyMap ++ BoxedJdiProxy.primitiveToProxyMap).get(typeName)
  }

  /**
   * Get valid type name from context.
   *
   * If not present it is added.
   * If not stubbable, `typeName` is returned.
   */
  private def typeFromContext(typeName: String): String = {
    getProxyForType(typeName).getOrElse(
      if (couldBeStubbed(typeName)) {
        state.addStub(typeName, getFunctionsForType(typeName))
        stubName(typeName)
      } else typeName)
  }

  /** return all function for given type */
  private def getFunctionsForType(name: String): Set[FunctionStub] = stubs.get(name).getOrElse(Set())

  /** Excludes functions that should not be stubbed */
  private def shouldBeStubbed(function: FunctionStub): Boolean = function match {
    case FunctionStub("toString", _, Some("java.lang.String"), Seq(Seq()), Seq()) => false
    case FunctionStub(ScalaOther.constructorFunctionName, _, _, _, _) => false
    case _ => true
  }

  private val stubCodeGenerator = new StubCodeGenerator(this)

  /** Genrates code for an exisitng class (originally loaded in JVM) */
  private def generateStubForExistingClass(name: String): String = {
    val functions = stubs(name)
    s"""
        |case class ${typeNameFor(name)}(proxy: JdiProxy)
        |    extends JdiProxyWrapper(proxy) {
        |  ${functions.map(stubCodeGenerator.apply).mkString("\n\t")}
        |}
        """.stripMargin
  }

  /**  Generate stub for class that will be loaded in JVM - generated for this expression */
  private def generateStubForNewClass(name: String): String = {
    val data = state.newCodeClasses(name)
    val proxyParentClass = FunctionJdiProxy.functionToProxyMap(data.parentClassName)
    val className = typeNameFor(name)
    val realClassName = data.className
    s"""case class $className(context: JdiContext) extends $proxyParentClass { override val className = "$realClassName"}"""
  }

  /** Generate stub for single type */
  private def generateSingleStub(name: String): String = {
    state.newCodeClasses.get(name)
      .map(_ => generateStubForNewClass(name))
      .getOrElse(generateStubForExistingClass(name))
  }

  /** All notstubbable classes */
  private val notStubbable = Set(
    ScalaOther.nothingType,
    ScalaOther.unitType,
    ScalaOther.arrayType,

    //JdiProxy in all forms
    DebuggerSpecific.proxyName,
    DebuggerSpecific.proxyFullName,
    DebuggerSpecific.proxyObjectFullName,

    //JdiContext in all forms
    DebuggerSpecific.contextName,
    DebuggerSpecific.contextFullName,
    DebuggerSpecific.contextObjFullName) ++
    FunctionJdiProxy.functionToProxyMap.values ++
    FunctionJdiProxy.functionToProxyMap.keys

  /** Could class with given name be stubbed. */
  private def couldBeStubbed(stub: String): Boolean = {
    def isPrimitive(name: String) = ScalaPrimitivesUnified.all.contains(name)
    !(notStubbable.contains(stub) || isPrimitive(stub))
  }
}
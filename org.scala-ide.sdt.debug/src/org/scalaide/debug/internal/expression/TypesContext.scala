/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy
import org.scalaide.debug.internal.expression.proxies.Function1JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function10JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function6JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function13JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function15JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function11JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function20JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function7JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function17JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function5JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function19JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function8JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function18JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function0JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function16JdiProxy
import org.scalaide.debug.internal.expression.proxies.PartialFunctionProxy
import org.scalaide.debug.internal.expression.proxies.Function3JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function12JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function14JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function4JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function9JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function22JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function21JdiProxy
import org.scalaide.debug.internal.expression.proxies.Function2JdiProxy
import org.scalaide.debug.internal.expression.proxies.UnitJdiProxy

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

  /** Gets all types with function that are called   */
  def stubs: Map[String, Set[FunctionStub]] = _stubs

  /** Gets all newly generated class */
  def newCodeClasses: Map[String, ClassData] = _newCodeClasses

  /** Adds new stub to type state */
  def addStub(typeName: String, functions: Set[FunctionStub]): Unit = _stubs += typeName -> functions

  /** Adds new class to type state */
  def addNewClass(name: String, data: ClassData): Unit = _newCodeClasses += name -> data
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

  import TypesContext._

  private val state: TypesContextState = new TypesContextState()

  /** Classes to be loaded on debuged jvm. */
  def classesToLoad: Iterable[ClassData] = state.newCodeClasses.values

  /** Function stubs */
  def stubs: Map[String, Set[FunctionStub]] = state.stubs

  /**
   * Obtains type name for given tree
   * @param tree tree to search for
   */
  def treeTypeName(tree: universe.Tree): Option[String] = {
    if (tree.toString == names.thisNil) Some(names.nil)
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
  val byNameType: String = functionToProxyMap(ScalaFunctions.Function0)

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
   * Obtains type name for given tree.
   *
   * @param tree tree to search for
   * @param isObject if given tree represents an object
   */
  private def typeName(tpe: universe.Type, isObject: Boolean): Option[String] = {
    def correctTypes(oldName: String): Option[String] = oldName match {
      case names.thisList => Some(names.list)
      case names.thisNil => Some(names.nil)
      case any if isObject => Some(JdiContext.toObject(any))
      case ScalaOther.nothingType => None
      case any => Some(any)
    }

    (tpe match {
      case typeRef: universe.TypeRef @unchecked => Some(typeRef.typeSymbol.fullName)
      case singleType: universe.SingleType @unchecked => Some(singleType.typeSymbol.fullName)
      case method: universe.MethodType @unchecked => typeName(method.resultType, isObject = method.resultType.typeSymbol.isModule)
      case constantType: universe.ConstantType @unchecked => Some(constantType.typeSymbol.fullName)
      case thisType: universe.ThisType @unchecked => Some(thisType.typeSymbol.fullName)
      case any => throw new RuntimeException(s"Unsupported tree shape: $any.")
    }).flatMap(correctTypes)
  }

  private val customProxyTypeMap = functionToProxyMap ++ primitiveToProxyMap

  /** Genereate stub name for given type - if not a special case just replace . with _ on full name */
  private def stubName(orginalType: String): String = {
    orginalType match {
      case DebuggerSpecific.proxyFullName => DebuggerSpecific.proxyName
      case DebuggerSpecific.contextFullName => DebuggerSpecific.contextName
      case DebuggerSpecific.proxyName => DebuggerSpecific.proxyName
      case DebuggerSpecific.contextName => DebuggerSpecific.contextName
      case other => other.replace(".", "_")
    }
  }

  /**
   * Get valid type name from context.
   *
   * If not present it is added.
   * If not stubbable, `typeName` is returned.
   */
  private def typeFromContext(typeName: String): String = {
    customProxyTypeMap.get(typeName).getOrElse {
      if (couldBeStubbed(typeName)) {
        state.addStub(typeName, getFunctionsForType(typeName))
        stubName(typeName)
      } else typeName
    }
  }

  /** return all function for given type */
  private def getFunctionsForType(name: String): Set[FunctionStub] = stubs.get(name).getOrElse(Set())

  /** Excludes functions that should not be stubbed */
  private def shouldBeStubbed(function: FunctionStub): Boolean = function match {
    case FunctionStub("toString", Some("java.lang.String"), Seq(Seq()), Seq()) => false
    case FunctionStub(ScalaOther.constructorFunctionName, _, _, _) => false
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
    val proxtParentClass = functionToProxyMap(data.parentClassName)
    s"""case class ${typeNameFor(name)}(context: JdiContext) extends $proxtParentClass { override val className = "${data.className}"}"""
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

    // rich types
    ScalaRichTypes.Boolean,

    //JdiProxy in all forms
    DebuggerSpecific.proxyName,
    DebuggerSpecific.proxyFullName,
    DebuggerSpecific.proxyObjectFullName,

    //JdiContext in all forms
    DebuggerSpecific.contextName,
    DebuggerSpecific.contextFullName,
    DebuggerSpecific.contextObjFullName) ++ functionToProxyMap.values ++ functionToProxyMap.keys

  /** Could class with given name be stubbed. */
  private def couldBeStubbed(stub: String): Boolean = {
    def isPrimitive(name: String) = ScalaPrimitivesUnified.all.contains(name)
    !(notStubbable.contains(stub) || isPrimitive(stub))
  }
}

/** Some static data (names and relations) used in TypesContext. */
protected object TypesContext {

  private val functionToProxyMap = Map(
    ScalaFunctions.PartialFunction -> classOf[PartialFunctionProxy].getSimpleName,
    ScalaFunctions.Function0 -> classOf[Function0JdiProxy].getSimpleName,
    ScalaFunctions.Function1 -> classOf[Function1JdiProxy].getSimpleName,
    ScalaFunctions.Function2 -> classOf[Function2JdiProxy].getSimpleName,
    ScalaFunctions.Function3 -> classOf[Function3JdiProxy].getSimpleName,
    ScalaFunctions.Function4 -> classOf[Function4JdiProxy].getSimpleName,
    ScalaFunctions.Function5 -> classOf[Function5JdiProxy].getSimpleName,
    ScalaFunctions.Function6 -> classOf[Function6JdiProxy].getSimpleName,
    ScalaFunctions.Function7 -> classOf[Function7JdiProxy].getSimpleName,
    ScalaFunctions.Function8 -> classOf[Function8JdiProxy].getSimpleName,
    ScalaFunctions.Function9 -> classOf[Function9JdiProxy].getSimpleName,
    ScalaFunctions.Function10 -> classOf[Function10JdiProxy].getSimpleName,
    ScalaFunctions.Function11 -> classOf[Function11JdiProxy].getSimpleName,
    ScalaFunctions.Function12 -> classOf[Function12JdiProxy].getSimpleName,
    ScalaFunctions.Function13 -> classOf[Function13JdiProxy].getSimpleName,
    ScalaFunctions.Function14 -> classOf[Function14JdiProxy].getSimpleName,
    ScalaFunctions.Function15 -> classOf[Function15JdiProxy].getSimpleName,
    ScalaFunctions.Function16 -> classOf[Function16JdiProxy].getSimpleName,
    ScalaFunctions.Function17 -> classOf[Function17JdiProxy].getSimpleName,
    ScalaFunctions.Function18 -> classOf[Function18JdiProxy].getSimpleName,
    ScalaFunctions.Function19 -> classOf[Function19JdiProxy].getSimpleName,
    ScalaFunctions.Function20 -> classOf[Function20JdiProxy].getSimpleName,
    ScalaFunctions.Function21 -> classOf[Function21JdiProxy].getSimpleName,
    ScalaFunctions.Function22 -> classOf[Function22JdiProxy].getSimpleName)

  private val primitiveToProxyMap = Map(
    ScalaPrimitivesUnified.Byte -> classOf[ByteJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Short -> classOf[ShortJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Int -> classOf[IntJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Long -> classOf[LongJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Double -> classOf[DoubleJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Float -> classOf[FloatJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Char -> classOf[CharJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Boolean -> classOf[BooleanJdiProxy].getSimpleName,

    ScalaOther.unitType -> classOf[UnitJdiProxy].getSimpleName,

    ScalaRichTypes.Boolean -> classOf[BooleanJdiProxy].getSimpleName,

    JavaBoxed.Byte -> classOf[ByteJdiProxy].getSimpleName,
    JavaBoxed.Short -> classOf[ShortJdiProxy].getSimpleName,
    JavaBoxed.Integer -> classOf[IntJdiProxy].getSimpleName,
    JavaBoxed.Long -> classOf[LongJdiProxy].getSimpleName,
    JavaBoxed.Double -> classOf[DoubleJdiProxy].getSimpleName,
    JavaBoxed.Float -> classOf[FloatJdiProxy].getSimpleName,
    JavaBoxed.Character -> classOf[CharJdiProxy].getSimpleName,
    JavaBoxed.Boolean -> classOf[BooleanJdiProxy].getSimpleName,
    JavaBoxed.String -> classOf[StringJdiProxy].getSimpleName)

  object names {
    val partialFunction = "scala.PartialFunction"

    val nil = "scala.collection.immutable.Nil"
    // strange value that shows up instead of above one
    val thisNil = "immutable.this.Nil"

    val list = "scala.collection.immutable.List"
    // strange value that shows up instead of above one
    val thisList = "immutable.this.List"
  }
}
/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.TypeRef

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

import Names.Debugger
import Names.Java
import Names.Scala

/**
 * Represents new class to be loaded on remote .
 *
 * @param className name of class
 * @param code array of bytes with class contents
 * @param constructorArgsTypes
 */
case class ClassData(className: String, code: Array[Byte], constructorArgsTypes: Seq[String])

/**
 * Wrapper on variable name, not to use raw Scala AST types.
 */
class Variable(val name: universe.TermName)

object Variable {
  def apply(name: universe.TermName): Variable = new Variable(name)
}

/**
 * Unbound variable used in expression.
 *
 * @param isLocal true if variable is defined inside current frame (method).
 */
case class UnboundVariable(override val name: universe.TermName, isLocal: Boolean) extends Variable(name)

/**
 * Represents mutable (append-only) state of [[org.scalaide.debug.internal.expression.TypesContext]].
 */
final class TypesContextState() {

  /** Maps function names to it's stubs */
  private var _newCodeClasses: Map[String, ClassData] = Map.empty

  /** Holds all unbound variables */
  private var _unboundVariables: Set[UnboundVariable] = Set.empty

  /** Gets all newly generated class */
  def newCodeClasses: Map[String, ClassData] = _newCodeClasses

  /** Gets all unbound variables */
  def unboundVariables: Set[UnboundVariable] = _unboundVariables

  /** Adds new class to type state */
  def addNewClass(name: String, data: ClassData): Unit = _newCodeClasses += name -> data

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[UnboundVariable]): Unit = _unboundVariables ++= variables
}

/**
 * Contains all information of types obtained during compilation of expression
 * During phrases it is filled with types and function that is called on that types.
 * It generates mapping for names - plain JVM names are translated to it proxy versions.
 *
 * WARNING - this class have mutable internal state
 */
final class TypesContext() {

  private val state: TypesContextState = new TypesContextState()

  /** Classes to be loaded on debugged jvm. */
  def classesToLoad: Iterable[ClassData] = state.newCodeClasses.values

  /** Gets all unbound variables */
  def unboundVariables: Set[UnboundVariable] = state.unboundVariables

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[UnboundVariable]): Unit = state.addUnboundVariables(variables)

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
    jvmCode: Array[Byte],
    constructorArgsTypes: Seq[String]): String = {
    state.addNewClass(proxyType, ClassData(className, jvmCode, constructorArgsTypes))
    className
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
        case other =>
          val typeSignature = other.typeSignature.toString()
          if (typeSignature.endsWith(".type")) Some(typeSignature)
          else Some(singleType.typeSymbol.fullName)
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

}

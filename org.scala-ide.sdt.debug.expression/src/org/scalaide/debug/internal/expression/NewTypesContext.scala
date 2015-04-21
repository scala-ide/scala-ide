/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

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
 * Represents mutable (append-only) state of [[org.scalaide.debug.internal.expression.NewTypesContext]].
 */
final class NewTypesContextState() {

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
 * During the GenerateStub phase type context is used to create stub classes.
 *
 * WARNING - this class have mutable internal state
 */
final class NewTypesContext() {

  private val state: NewTypesContextState = new NewTypesContextState()

  /** Classes to be loaded on debugged jvm. */
  def classesToLoad: Iterable[ClassData] = state.newCodeClasses.values

  /** Gets all unbound variables */
  def unboundVariables: Set[UnboundVariable] = state.unboundVariables

  /** Add unbound variables to scope */
  def addUnboundVariables(variables: Set[UnboundVariable]): Unit = state.addUnboundVariables(variables)

  /**
   * Creates a new type that will be loaded in debugged jvm.
   *
   * @param proxyType type of proxy for new type e.g. Function2JdiProxy
   * @param className name of class in jvm - must be same as in compiled code
   * @param jvmCode code of compiled class
   * @param constructorArgsTypes
   */
  def createNewType(proxyType: String,
    className: String,
    jvmCode: Array[Byte],
    constructorArgsTypes: Seq[String]): Unit =
    state.addNewClass(proxyType, ClassData(className, jvmCode, constructorArgsTypes))
}

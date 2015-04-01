/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.reflect.runtime.universe.TermName

sealed trait TypedVariable {
  /** type like collection.immutable.List (class name without generics) */
  def plainType: String

  def untyped: Variable

  def name: TermName = untyped.name
}

object TypedVariable {
  def apply(variable: Variable, plainType: String, genericType: Option[String]): TypedVariable =
    genericType match {
      case None => PlainTypedVariable(variable, plainType)
      case Some(genericType) => GenericVariable(variable, plainType, genericType)
    }
}

/**
 * @param plainType type like collection.immutable.List (class name without generics)
 * @param genericType generic signature of type from JDI
 */
case class GenericVariable(untyped: Variable, plainType: String, genericType: String) extends TypedVariable

case class PlainTypedVariable(untyped: Variable, plainType: String) extends TypedVariable

/** Marker, used when working without classpath */
trait DynamicVariable

/** Variable that come from dynamic-typed this - used without classpath */
case class DynamicMemberBasedVariable(untyped: Variable, memberName: String) extends TypedVariable with DynamicVariable {
  override def plainType: String = Names.Debugger.proxyFullName
}

/** Method (mock as variable) that come from dynamic-typed this - used without classpath */
case class DynamicMemberBasedMethod(untyped: Variable, memberName: String, arity: Int) extends TypedVariable with DynamicVariable {
  override def plainType: String = Names.Debugger.proxyFullName
}

/**
 * Contains information required to run nested method
 * @param on - object that contains required method
 * @param jvmName - name real jvm methods (like name$1)
 * @param argsNames - names of arguments
 */
case class NestedMethodImplementation(on: TermName, jvmName: String, argsNames: Seq[String])

/**
 * All information about nested method from presentation compiler used to match this method to real runtime version
 * @param parametersListsCount
 */
case class NestedMethodDeclaration(name: String, startLine: Int, endLine: Int, argumentsCount: Int, parametersListsCount: Int)

/**
 * Represents variables in context of suspended debugging.
 *
 * Allows you to get information about types.
 */
trait VariableContext extends Any {

  /** Is classpath present in this context */
  def hasClasspath: Boolean

  /**
   * Looks up a type of variable with a given name.
   * Returns Some if there is variable in scope
   * Returns `None` if variable is not defined in current scope.
   */
  def typed(variableName: Variable): Option[TypedVariable]

  /**
   * Name of enclosing package.
   * If context shows class 'a.b.C' returns Some("a.b").
   * If debug is stopped outside of any class or object returns false.
   */
  def thisPackage: Option[String]

  /**
   * Get list of all synthetic variables for given expression.
   * They are e.g. different version of `this`.
   */
  def syntheticVariables: Seq[Variable]

  /**
   * Get list of all synthetic imports for given expression.
   * They are e.g. imports from this or outer fields.
   */
  def syntheticImports: Seq[String]

  /**
   * Try to implement a value
   * When returning Some with string implementation for given value it alters default value implementation.
   * @param name name of variable to implement
   */
  def implementValue(name: TermName): Option[String]

  /** Try to implement nested method base on it's declaration */
  def nestedMethodImplementation(method: NestedMethodDeclaration): Option[NestedMethodImplementation]

  /**
   * Set of names of variables local to frame in which thread is suspended.
   * This contains `val`s and `var`s defined in current method.
   */
  def localVariablesNames(): Set[String]
}

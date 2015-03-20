/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.reflect.runtime.universe.TermName

sealed trait VariableType {
  /** type like collection.immutable.List (class name without generics) */
  def plainType: String
}

object VariableType {
  def apply(plainType: String, genericType: Option[String]): VariableType =
    genericType match {
      case None => PlainVariableType(plainType)
      case Some(genericType) => GenericVariableType(plainType, genericType)
    }
}
/**
 *
 * @param plainType type like collection.immutable.List (class name without generics)
 * @param genericType generic signature of type from JDI
 */
case class GenericVariableType(plainType: String, genericType: String) extends VariableType

case class PlainVariableType(plainType: String) extends VariableType

/**
 * Contains information required to run nested method
 * @param on - object that contains required method
 * @param jvmName - name real jvm methods (like name$1)
 * @param argsNames - names of aruments
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

  /**
   * Looks up a type of variable with a given name.
   * Returns Some if there is variable in scope
   * Returns `None` if variable is not defined in current scope.
   */
  def typeOf(variableName: TermName): Option[VariableType]

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
   *  They are e.g. imports from this or outer fields
   */
  def syntheticImports: Seq[String]

  /**
   * Try to implement a value
   *  When returning Some with string implementation for given value it alters default value implementation
   *  @param name name of variable to implement
   */
  def implementValue(name: TermName): Option[String]

  /** Try to implement nested method base on it's declaration */
  def nestedMethodImplementation(function: NestedMethodDeclaration): Option[NestedMethodImplementation]

  /**
   * Set of names of variables local to frame in which thread is suspended.
   * This contains `val`s and `var`s defined in current method.
   */
  def localVariablesNames(): Set[String]
}

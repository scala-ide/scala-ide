/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.reflect.runtime.universe.TermName

/**
 * Represents variables in context of suspended debugging.
 *
 * Allows you to get information about types.
 */
trait VariableContext extends Any {

  /**
   * Looks up a type of variable with a given name.
   * Returns pure Scala type like `collection.immutable.List[Int]` or `collection.immutable.Nil.type`
   * Returns `None` if variable is not defined in current scope.
   */
  def typeOf(variableName: TermName): Option[String]

  /**
   * Name of enclosing package.
   * If context shows class 'a.b.C' returns Some("a.b").
   * If debug is stopped outside of any class or object returns false.
   */
  def thisPackage: Option[String]

  /**
   * Get list of all synthetic variables for given expression.
   *  They are e.g. different version of this
   */
  def syntheticVariables: Seq[TermName]

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
}
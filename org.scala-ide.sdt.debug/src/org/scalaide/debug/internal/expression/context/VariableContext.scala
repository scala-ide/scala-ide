/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

/**
 * Represents variables in context of suspended debugging.
 *
 * Allows you to get information about types.
 */
trait VariableContext extends Any {

  /**
   * Looks up a type of variable with a given name.
   * Returns `None` if variable is not defined in current scope.
   */
  def getType(variableName: String): Option[String]

  /**
   * Name of enclosing package.
   * If context shows class 'a.b.C' returns Some("a.b").
   * If debug is stopped outside of any class or object returns false.
   */
  def getThisPackage: Option[String]
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.util.Try

import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy

import com.sun.jdi.Location
import com.sun.jdi.ThreadReference
import org.scalaide.debug.internal.expression.proxies.JdiProxy

/**
 * Evaluates conditions for conditional breakpoints.
 */
class ConditionManager {
  import ExpressionEvaluator._

  // type alias for clarity
  private type Code = (String, Location)

  /** Cache for conditions to not re-compile them */
  private var conditionMap: Map[Code, Try[JdiExpression]] = Map()

  /**
   * Evaluates given condition in context of some breakpoint.
   *
   * @param condition code to compile and check (must evaluate to boolean)
   * @param location location of breakpoint
   * @param compile function that is able to compile string expression
   * @param run fucntion that is able to run and get result for given compiled expression
   * @return Success(evaluated value) or Failure(reason why compilation of condition failed)
   */
  def checkCondition(condition: String,
                     location: Location)(
                      compile: String => Try[JdiExpression])(
                      run: JdiExpression => JdiProxy): Try[Boolean] = {

    conditionMap.get(condition -> location).getOrElse {
      val expr = compile(condition)
      conditionMap += (condition -> location) -> expr
      expr
    }.map(run).map {
      case boleanProxy: BooleanJdiProxy => boleanProxy.booleanValue
      case result => throw new NoBooleanJdiProxyException(result.objectType.name)
    }
  }

  /** Thrown when result is not a proxy for boolean value. */
  class NoBooleanJdiProxyException(objectType: String)
    extends RuntimeException(s"type: $objectType is not a Boolean!")

}

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy

import com.sun.jdi.Location

import ExpressionEvaluator.JdiExpression

/**
 * Evaluates conditions for conditional breakpoints.
 */
class ConditionManager {

  import ExpressionEvaluator._

  // type alias for clarity
  private type Code = (String, Location)

  /**
   * Cache for conditions to not re-compile them.
   *
   * Access to this map is not synchronized as it's only a cache for compiled conditions
   * and in worst-case scenario some condition will be compiled twice.
   */
  private var conditionMap: Map[Code, Try[JdiExpression]] = Map()

  /**
   * Evaluates given condition in context of some breakpoint.
   *
   * @param condition code to compile and check (must evaluate to boolean)
   * @param location location of breakpoint
   * @param compile function that is able to compile string expression
   * @param run function that is able to run and get result for given compiled expression
   * @return Success(evaluated value) or Failure(reason why compilation of condition failed)
   */
  def checkCondition(condition: String,
    location: Location)(
      compile: String => Try[JdiExpression])(
        run: JdiExpression => JdiProxy): Try[Boolean] = {

    val compiledExpression = conditionMap.get(condition -> location).getOrElse {
      val expr = compile(condition)
      conditionMap += (condition -> location) -> expr
      expr
    }

    val result = for {
      expression <- compiledExpression
      res <- Try(run(expression))
    } yield res

    result.flatMap {
      case booleanProxy: BooleanJdiProxy => Success(booleanProxy._BooleanMirror)
      case result => Failure(new NoBooleanJdiProxyException(result.referenceType.name))
    }
  }
}

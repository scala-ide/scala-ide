/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy

/**
 * Implements comparison for numeric proxies where using conversion to double for each operand.
 */
trait NumericComparison {
  self: NumberJdiProxy[_, _] =>

  /** Helper for comparison methods. */
  private def applyForNumbers(operation: (Double, Double) => Boolean)(v: NumberJdiProxy[_, _]): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(
      operation(toDouble.primitiveValue, v.toDouble.primitiveValue),
      proxyContext)

  /** Lesser than function */
  val < = applyForNumbers(_ < _) _

  /** Greater than function */
  val > = applyForNumbers(_ > _) _

  /** Lesser than or equals function */
  val <= = applyForNumbers(_ <= _) _

  /** Greater than or equals function */
  val >= = applyForNumbers(_ >= _) _
}
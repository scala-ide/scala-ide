/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import com.sun.jdi.IntegerValue
import org.scalaide.debug.internal.expression.proxies.JdiProxy

/**
 * Implements comparison for boolean proxies where both operands have the same type.
 */
trait BooleanComparison {
  self: BooleanJdiProxy =>

  /** Helper for comparison methods. */
  private def applyForBooleans(operation: (Boolean, Boolean) => Boolean)(v: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(
      operation(booleanValue, v.booleanValue),
      context)

  /** Lesser than function */
  val < = applyForBooleans(_ < _) _

  /** Greater than function */
  val > = applyForBooleans(_ > _) _

  /** Lesser than or equals function */
  val <= = applyForBooleans(_ <= _) _

  /** Greater than or equals function */
  val >= = applyForBooleans(_ >= _) _
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.conditional

import org.junit.Test
import org.scalaide.debug.internal.ConditionContext
import org.scalaide.debug.internal.expression.JavaBoxed

object ConditionalBreakpointsTest extends BaseConditionalBreakpointTestCompanion

class ConditionalBreakpointsTest extends BaseConditionalBreakpointTest(ConditionalBreakpointsTest) {

  @Test
  def shouldNotStopAtBreakpoint(): Unit = {
    testWithCondition("int", ConditionContext("int == 2", shouldSuspend = false))
  }

  @Test
  def shouldStopAtBreakpoint(): Unit = {
    testWithCondition("int", ConditionContext("int == 1", shouldSuspend = true))
  }

  @Test
  def shouldStopAtBreakpointAndCorrectlyEvaluateExpression(): Unit = {
    testWithCondition("int", ConditionContext("int == 1", shouldSuspend = true),
      expectedValueContext = Some(ExpectedValueContext("1", JavaBoxed.Integer)))
  }

  @Test(expected = classOf[AssertionError])
  def shouldThrowExceptionBecauseOfNotStoppingAtBreakpoint(): Unit = {
    testWithCondition("int", ConditionContext("int == 2", shouldSuspend = true))
  }

  @Test(expected = classOf[AssertionError])
  def shouldThrowExceptionBecauseOfStoppingAtBreakpoint(): Unit = {
    testWithCondition("int", ConditionContext("int == 1", shouldSuspend = false))
  }

  @Test
  def shouldStopOnExceptionInCondition(): Unit = {
    testWithCondition("int", ConditionContext("???", shouldSuspend = true))
  }

  @Test
  def shouldStopWhenConditionIsEmpty(): Unit = {
    testWithCondition("int", ConditionContext("", shouldSuspend = true))
  }

}

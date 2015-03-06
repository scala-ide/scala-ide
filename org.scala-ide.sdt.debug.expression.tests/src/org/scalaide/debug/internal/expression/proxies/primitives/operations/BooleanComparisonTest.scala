/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class BooleanComparisonTest extends BaseIntegrationTest(BooleanComparisonTest) {

  import TestValues.ValuesTestCase._
  import TestValues.any2String

  @Test
  def `!boolean`(): Unit = eval("!boolean", !boolean, Java.boxed.Boolean)

  @Test
  def equality(): Unit = {
    eval("boolean == boolean", boolean == boolean, Java.boxed.Boolean)
    eval("boolean == boolean2", boolean == boolean2, Java.boxed.Boolean)
    eval("boolean2 == boolean2", boolean2 == boolean2, Java.boxed.Boolean)
    eval("boolean2 == boolean", boolean2 == boolean, Java.boxed.Boolean)
  }

  @Test
  def inequality(): Unit = {
    eval("boolean != boolean", boolean != boolean, Java.boxed.Boolean)
    eval("boolean != boolean2", boolean != boolean2, Java.boxed.Boolean)
    eval("boolean2 != boolean2", boolean2 != boolean2, Java.boxed.Boolean)
    eval("boolean2 != boolean", boolean2 != boolean, Java.boxed.Boolean)
  }

  @Test
  def lesserThan(): Unit = {
    eval("boolean < boolean", boolean < boolean, Java.boxed.Boolean)
    eval("boolean < boolean2", boolean < boolean2, Java.boxed.Boolean)
    eval("boolean2 < boolean2", boolean2 < boolean2, Java.boxed.Boolean)
    eval("boolean2 < boolean", boolean2 < boolean, Java.boxed.Boolean)
  }

  @Test
  def greaterThan(): Unit = {
    eval("boolean > boolean", boolean > boolean, Java.boxed.Boolean)
    eval("boolean > boolean2", boolean > boolean2, Java.boxed.Boolean)
    eval("boolean2 > boolean2", boolean2 > boolean2, Java.boxed.Boolean)
    eval("boolean2 > boolean", boolean2 > boolean, Java.boxed.Boolean)
  }

  @Test
  def lesserThanOrEqual(): Unit = {
    eval("boolean <= boolean", boolean <= boolean, Java.boxed.Boolean)
    eval("boolean <= boolean2", boolean <= boolean2, Java.boxed.Boolean)
    eval("boolean2 <= boolean2", boolean2 <= boolean2, Java.boxed.Boolean)
    eval("boolean2 <= boolean", boolean2 <= boolean, Java.boxed.Boolean)
  }

  @Test
  def greaterThanOrEqual(): Unit = {
    eval("boolean >= boolean", boolean >= boolean, Java.boxed.Boolean)
    eval("boolean >= boolean2", boolean >= boolean2, Java.boxed.Boolean)
    eval("boolean2 >= boolean2", boolean2 >= boolean2, Java.boxed.Boolean)
    eval("boolean2 >= boolean", boolean2 >= boolean, Java.boxed.Boolean)
  }

}

object BooleanComparisonTest extends BaseIntegrationTestCompanion

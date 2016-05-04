/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues

class BooleanComparisonTest extends BaseIntegrationTest(BooleanComparisonTest) {

  import TestValues.ValuesTestCase._

  @Test
  def `!boolean`(): Unit = eval("!boolean", !boolean, Java.primitives.boolean)

  @Test
  def equality(): Unit = {
    eval("boolean == boolean", boolean == boolean, Java.primitives.boolean)
    eval("boolean == boolean2", boolean == boolean2, Java.primitives.boolean)
    eval("boolean2 == boolean2", boolean2 == boolean2, Java.primitives.boolean)
    eval("boolean2 == boolean", boolean2 == boolean, Java.primitives.boolean)
  }

  @Test
  def inequality(): Unit = {
    eval("boolean != boolean", boolean != boolean, Java.primitives.boolean)
    eval("boolean != boolean2", boolean != boolean2, Java.primitives.boolean)
    eval("boolean2 != boolean2", boolean2 != boolean2, Java.primitives.boolean)
    eval("boolean2 != boolean", boolean2 != boolean, Java.primitives.boolean)
  }

  @Test
  def lesserThan(): Unit = {
    eval("boolean < boolean", boolean < boolean, Java.primitives.boolean)
    eval("boolean < boolean2", boolean < boolean2, Java.primitives.boolean)
    eval("boolean2 < boolean2", boolean2 < boolean2, Java.primitives.boolean)
    eval("boolean2 < boolean", boolean2 < boolean, Java.primitives.boolean)
  }

  @Test
  def greaterThan(): Unit = {
    eval("boolean > boolean", boolean > boolean, Java.primitives.boolean)
    eval("boolean > boolean2", boolean > boolean2, Java.primitives.boolean)
    eval("boolean2 > boolean2", boolean2 > boolean2, Java.primitives.boolean)
    eval("boolean2 > boolean", boolean2 > boolean, Java.primitives.boolean)
  }

  @Test
  def lesserThanOrEqual(): Unit = {
    eval("boolean <= boolean", boolean <= boolean, Java.primitives.boolean)
    eval("boolean <= boolean2", boolean <= boolean2, Java.primitives.boolean)
    eval("boolean2 <= boolean2", boolean2 <= boolean2, Java.primitives.boolean)
    eval("boolean2 <= boolean", boolean2 <= boolean, Java.primitives.boolean)
  }

  @Test
  def greaterThanOrEqual(): Unit = {
    eval("boolean >= boolean", boolean >= boolean, Java.primitives.boolean)
    eval("boolean >= boolean2", boolean >= boolean2, Java.primitives.boolean)
    eval("boolean2 >= boolean2", boolean2 >= boolean2, Java.primitives.boolean)
    eval("boolean2 >= boolean", boolean2 >= boolean, Java.primitives.boolean)
  }

}

object BooleanComparisonTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll

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

class NumericComparisonTest extends BaseIntegrationTest(NumericComparisonTest) {

  import TestValues.ValuesTestCase._

  private def baseBooleanTest(operator: String, expected: Boolean): Unit =
    eval(s"int $operator int2", expected.toString, Java.primitives.boolean) // compare to 1

  @Test
  def integerComparisonTest(): Unit = {
    baseBooleanTest("<", true)
    baseBooleanTest(">", false)
    baseBooleanTest(">=", false)
    baseBooleanTest("<=", true)
    baseBooleanTest("==", false)
    baseBooleanTest("!=", true)
  }

  @Test
  def testComparisionForDifferentTypes(): Unit = {
    eval("int == double", int == double, Java.primitives.boolean)
    eval("int != double", int != double, Java.primitives.boolean)
    eval("1 < 5L", 1 < 5L, Java.primitives.boolean)
    eval("int < 5L", int < 5L, Java.primitives.boolean)
    eval("int < long", int < long, Java.primitives.boolean)
    eval("int <= long", int <= long, Java.primitives.boolean)
    eval("int > long", int > long, Java.primitives.boolean)
    eval("int >= long", int >= long, Java.primitives.boolean)
    eval("short >= double", short >= double, Java.primitives.boolean)
    eval("long >= int", long >= int, Java.primitives.boolean)
    eval("double >= short", double >= short, Java.primitives.boolean)
  }
}

object NumericComparisonTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll

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

class NumericComparisonTest extends BaseIntegrationTest(NumericComparisonTest) {

  import TestValues.ValuesTestCase._

  private def baseBooleanTest(operator: String, expected: Boolean): Unit =
    eval(s"int $operator int2", expected.toString, Java.boxed.Boolean) // compare to 1

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
    eval("int == double", int == double, Java.boxed.Boolean)
    eval("int != double", int != double, Java.boxed.Boolean)
    eval("1 < 5L", 1 < 5L, Java.boxed.Boolean)
    eval("int < 5L", int < 5L, Java.boxed.Boolean)
    eval("int < long", int < long, Java.boxed.Boolean)
    eval("int <= long", int <= long, Java.boxed.Boolean)
    eval("int > long", int > long, Java.boxed.Boolean)
    eval("int >= long", int >= long, Java.boxed.Boolean)
    eval("short >= double", short >= double, Java.boxed.Boolean)
    eval("long >= int", long >= int, Java.boxed.Boolean)
    eval("double >= short", double >= short, Java.boxed.Boolean)
  }
}

object NumericComparisonTest extends BaseIntegrationTestCompanion

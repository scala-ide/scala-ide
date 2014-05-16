/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class NumericComparisonTest extends BaseIntegrationTest(NumericComparisonTest) {

  import TestValues.Values._
  import TestValues.any2String

  private def baseBooleanTest(operator: String, expected: Boolean): Unit =
    eval(s"int $operator int2", expected.toString, JavaBoxed.Boolean) // compare to 1

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
    eval("int == double", int == double, JavaBoxed.Boolean)
    eval("int != double", int != double, JavaBoxed.Boolean)
    eval("1 < 5L", 1 < 5L, JavaBoxed.Boolean)
    eval("int < 5L", int < 5L, JavaBoxed.Boolean)
    eval("int < long", int < long, JavaBoxed.Boolean)
    eval("int <= long", int <= long, JavaBoxed.Boolean)
    eval("int > long", int > long, JavaBoxed.Boolean)
    eval("int >= long", int >= long, JavaBoxed.Boolean)
    eval("short >= double", short >= double, JavaBoxed.Boolean)
    eval("long >= int", long >= int, JavaBoxed.Boolean)
    eval("double >= short", double >= short, JavaBoxed.Boolean)
  }
}

object NumericComparisonTest extends BaseIntegrationTestCompanion

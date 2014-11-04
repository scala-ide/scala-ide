/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.junit.Test
import org.scalaide.debug.internal.expression.TestValues.ValuesTestCase

class AppObjectTest extends BaseIntegrationTest(AppObjectTest) {

  @Test
  def testField(): Unit =
    eval("outer", "ala", Names.Java.boxed.String)

  @Test
  def testThis(): Unit =
    eval("this", "object Ala", "debug.Values$")

  @Test
  def testMethod(): Unit =
    eval("alaMethod(2)", "ala 2", Names.Java.boxed.String)
}

object AppObjectTest extends BaseIntegrationTestCompanion(lineNumber = ValuesTestCase.breakpointLineForAppObjectTest)

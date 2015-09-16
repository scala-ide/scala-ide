/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.TestValues.AppObjectTestCase

class AppObjectTest extends BaseIntegrationTest(AppObjectTest) {

  @Test
  def testField(): Unit =
    eval("outer", "ala", Names.Java.String)

  @Test
  def testThis(): Unit =
    eval("this", "object Ala", "debug.Values$")

  @Test
  def testMethod(): Unit =
    eval("alaMethod(2)", "ala 2", Names.Java.String)
}

object AppObjectTest extends BaseIntegrationTestCompanion(AppObjectTestCase)

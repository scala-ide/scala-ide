/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class GenericsTest extends BaseIntegrationTest(GenericsTest) with DefaultBeforeAfterEach {

  @Test
  def testFieldOfGenericTypeFromClass(): Unit =
    eval("a", 1, Java.primitives.int)

  @Test
  def testFieldOfGenericTypeFromMethod(): Unit =
    eval("b", "ala", Java.String)

  @Test
  def testFieldOfGenericTypeFromMethodThatRequiresExactType(): Unit =
    eval("b.filter('a' ==)", "aa", Java.String)
}

object GenericsTest extends BaseIntegrationTestCompanion(TestValues.GenericsTestCase) with DefaultBeforeAfterAll {
  override def typeName = "debug.GenericClass"
}

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.TestValues

class GenericsTest extends BaseIntegrationTest(GenericsTest) {

  @Test
  def testFieldOfGenericTypeFromClass(): Unit =
    eval("a", "1", Names.Java.boxed.Integer)

  @Test
  def testFieldOfGenericTypeFromMethod(): Unit =
    eval("b", "ala", Names.Java.boxed.String)

}

object GenericsTest extends BaseIntegrationTestCompanion(TestValues.GenericsTestCase) {
  override def typeName = "debug.GenericClass"
}

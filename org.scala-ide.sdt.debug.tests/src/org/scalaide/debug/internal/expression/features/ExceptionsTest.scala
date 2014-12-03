/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues.ExceptionsTestCase

class ExceptionsTest extends BaseIntegrationTest(ExceptionsTest) {

  @Test(expected = classOf[com.sun.jdi.InvocationException])
  def testCallingMethodThatThrowsAnException(): Unit = eval("throwing.foo(1)", "n/a", "n/a")

}

object ExceptionsTest extends BaseIntegrationTestCompanion(ExceptionsTestCase)

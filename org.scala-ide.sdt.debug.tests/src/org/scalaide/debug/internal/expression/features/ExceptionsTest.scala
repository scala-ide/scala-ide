/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.MethodInvocationException
import org.scalaide.debug.internal.expression.TestValues

class ExceptionsTest extends BaseIntegrationTest(ExceptionsTest) {

  @Test(expected = classOf[MethodInvocationException])
  def testCallingMethodThatThrowsAnException(): Unit = eval("throwing.foo(1)", "n/a", "n/a")

}

object ExceptionsTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.exceptionsFileName,
  lineNumber = TestValues.exceptionsLineNumber)

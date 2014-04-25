/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TestValues

/**
 * Tests if method arguments
 */
class MethodArgumentsAccessTest extends BaseIntegrationTest(MethodArgumentsAccessTest) {

  @Test
  def testIntArgument(): Unit = eval("int", "123", JavaBoxed.Integer)

  @Test
  def testDoubleArgument(): Unit = eval("double", "230.0", JavaBoxed.Double)

  @Test
  def testListArgument(): Unit = eval("list", "List(5, 10, 15)", ScalaOther.scalaList)

}

object MethodArgumentsAccessTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.argumentsFileName,
  lineNumber = TestValues.argumentsLine)

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.ArgumentsTestCase

/**
 * Tests if method arguments could be accessed in method's body.
 */
class MethodArgumentsAccessTest extends BaseIntegrationTest(MethodArgumentsAccessTest) {

  @Test
  def testIntArgument(): Unit = eval("int", 123, Java.boxed.Integer)

  @Test
  def testDoubleArgument(): Unit = eval("double", 230.0, Java.boxed.Double)

  @Test
  def testListArgument(): Unit = disableOnJava8 { eval("list", List(5, 10, 15), Scala.::) }

}

object MethodArgumentsAccessTest extends BaseIntegrationTestCompanion(ArgumentsTestCase)

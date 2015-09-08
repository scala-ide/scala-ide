/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Scala

class PartialFunctionLambdaTest extends BaseIntegrationTest(PartialFunctionLambdaTest) {

  @Test
  def testPartialFunctionAndPrimitives(): Unit =
    eval("list.filter { case a => a > 2 }", List(3), Scala.::)

  @Test
  def testPartialFunctionAndAnnotatedPrimitives(): Unit =
    eval("list.filter { case a: Int => a > 2 }", List(3), Scala.::)

  @Test
  def testPartialFunctionAndMulipleParameterLists(): Unit =
    eval("list.zip(list).map { case (a: Int, b: Int) => a + b }", List(2, 4, 6), Scala.::)
}

object PartialFunctionLambdaTest extends BaseIntegrationTestCompanion

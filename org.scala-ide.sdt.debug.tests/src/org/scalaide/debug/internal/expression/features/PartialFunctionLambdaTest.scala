/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class PartialFunctionLambdaTest extends BaseIntegrationTest(PartialFunctionLambdaTest) {

  @Ignore("TODO - O-5266 - add support for generic types")
  @Test
  def testPartialFunctionAndPrimitives(): Unit =
    eval("list.filter { case a => a > 2 }", "List(2,3)", Java.boxed.String)

  @Test
  def testPartialFunctionAndAnnotatedPrimitives(): Unit =
    eval("list.filter { case a: Int => a > 2 }", "List(3)", Scala.::)

  @Ignore("TODO - O-5266 - add support for multiple parameter typed partial function lambda")
  @Test
  def testPartialFunctionAndMulipleParameterLists(): Unit =
    eval("list.foldLeft(1){case (a: Int, b: Int) => a + b}", "7", Java.boxed.Integer)

}

object PartialFunctionLambdaTest extends BaseIntegrationTestCompanion
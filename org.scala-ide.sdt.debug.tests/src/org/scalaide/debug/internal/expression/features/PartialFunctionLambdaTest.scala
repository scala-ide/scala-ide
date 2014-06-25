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
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther

class PartialFunctionLambdaTest extends BaseIntegrationTest(PartialFunctionLambdaTest) {

  @Ignore("TODO - O-5266 - add support for generic types")
  @Test
  def `partial function and primitives: list.filter{case a => a > 2} `(): Unit =
    eval("list.filter{case a => a > 2}", "List(2,3)", JavaBoxed.String)

  @Test
  def `partial function and primitives: list.filter{case a: Int => a > 2} `(): Unit =
    eval("list.filter{case a: Int => a > 2}", "List(3)", ScalaOther.scalaList)

  @Ignore("TODO - O-5266 - add support for multiple parameter typed partial function lambda")
  @Test
  def `partial function and muliple parameter lists: list.foldLeft(1){case (a: Int, b: Int) => a + b} `(): Unit =
    eval("list.foldLeft(1){case (a: Int, b: Int) => a + b}", "7", JavaBoxed.Integer)

}

object PartialFunctionLambdaTest extends BaseIntegrationTestCompanion
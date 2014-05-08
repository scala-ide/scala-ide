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

class TypedLambdasTest extends BaseIntegrationTest(TypedLambdasTest) {

  // TODO - this test fails with assertion from toolbox compiler when `TypedLambdasTest` is run separately
  // Grzegorz Kossakowski said it's a bug in Scala and we could investigate it further and file a ticket
  @Test
  def `lambda with explicit type: list.map((_: Int) + 1) `(): Unit =
    eval("list.map((_: Int) - 1)", "List(0, 1, 2)", ScalaOther.scalaList)

  @Test
  def `lambda with explicit type: list.filter((_: Int) > 1) `(): Unit =
    eval("list.filter((_: Int) > 1)", "List(2, 3)", ScalaOther.scalaList)

  @Ignore("TODO - O-5160 - add support for closures")
  @Test
  def `lambda with explicit type and closure: list.map((_: Int) + 1) `(): Unit =
    eval("list.map((_: Int) - int)", "List(1, 2, 3)", ScalaOther.scalaList)

  @Test
  def higherOrderfunctionWithMultipleParameterListsOnValue(): Unit =
    eval("list.fold(0)((_: Int) + (_: Int))", "6", JavaBoxed.Integer)

}

object TypedLambdasTest extends BaseIntegrationTestCompanion
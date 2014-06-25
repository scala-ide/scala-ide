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
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.ScalaOther

class ImplicitTest extends BaseIntegrationTest(ImplicitTest) {

  // implicit parameters from companion object scope
  @Test
  def `new LibClassImplicits(1)`(): Unit =
    eval("new LibClassImplicits(1)", "LibClassImplicits(1)", "debug.LibClassImplicits")

  @Test
  def `new LibClass2ListsAndImplicits(1)(2)`(): Unit =
    eval("new LibClass2ListsAndImplicits(1)(2)", "LibClass2ListsAndImplicits(1)", "debug.LibClass2ListsAndImplicits")

  @Test
  def `libClass.withImplicitConversion(2)`(): Unit =
    eval("libClass.withImplicitConversion(2)", "2", JavaBoxed.Integer)

  @Test
  def `libClass.withImplicitParameter`(): Unit =
    eval("libClass.withImplicitParameter", "1", JavaBoxed.Integer)

  @Test
  def `scala collections implicits: List(1, 2).filter(_ > 1).head`(): Unit =
    eval("List(1, 2).filter(_ >= 2)", "List(2)", ScalaOther.scalaList)

  @Test
  def `scala collections implicits: List(1 -> 2, 2 -> 3).toMap`(): Unit =
    eval("List((1, 2), (2, 3)).toMap", "Map(1 -> 2, 2 -> 3)", "scala.collection.immutable.Map$Map2")

  // value from imports and local values

  @Ignore("0-5225 Add support for explicit implicits")
  @Test
  def `local val implicit: libClass.withImplicitIntParameter`(): Unit =
    eval("libClass.withImplicitIntParameter", "12", JavaBoxed.Integer)

  @Ignore("0-5225 Add support for explicit implicits")
  @Test
  def `explicit import implicit: ibClass.withImplicitStringParameter`(): Unit =
    eval("libClass.withImplicitStringParameter", "ala", JavaBoxed.String)

  @Ignore("0-5225 Add support for explicit implicits")
  @Test
  def `_ import implicit: libClass.withImplicitDoubleParameter`(): Unit =
    eval("libClass.withImplicitDoubleParameter", "1.1", JavaBoxed.Double)
}

object ImplicitTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.implicitsFileName,
  lineNumber = TestValues.implicitsLineNumber)

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

class LiteralsTest extends BaseIntegrationTest(LiteralsTest) {

  @Test
  def testUnitLiteral(): Unit = {
    eval("()", "()", ScalaOther.unitType)
  }

  @Test
  def testIntegerLiterals(): Unit = {
    eval("1", "1", JavaBoxed.Integer)
    eval("35", "35", JavaBoxed.Integer)
    eval("0xFFFFFFFF", "-1", JavaBoxed.Integer)
  }

  @Test
  def testLongLiterals(): Unit = {
    eval("1L", "1", JavaBoxed.Long)
    eval("123456789L", "123456789", JavaBoxed.Long)
  }

  @Test
  def testDoubleLiterals(): Unit = {
    eval("1.0", "1.0", JavaBoxed.Double)
    eval("1e30", "1.0E30", JavaBoxed.Double)
    eval("3.14159", "3.14159", JavaBoxed.Double)
    eval("1.0e100", "1.0E100", JavaBoxed.Double)
    eval(".1", "0.1", JavaBoxed.Double)
  }

  @Test
  def testBooleanLiterals(): Unit = {
    eval("false", "false", JavaBoxed.Boolean)
    eval("true", "true", JavaBoxed.Boolean)
  }

  @Test
  def testCharacterLiterals(): Unit = {
    eval("'c'", "c", JavaBoxed.Character)
    eval("'\u0041'", "A", JavaBoxed.Character)
    eval("'\t'", "\t", JavaBoxed.Character)
  }

  @Ignore("TODO - O-4549 - add support for floats")
  @Test
  def testFloatLiterals(): Unit = {
    eval("1.0f", "1.0", JavaBoxed.Float)
    eval("1e30f", "1.0E30", JavaBoxed.Float)
    eval("3.14159f", "3.14159", JavaBoxed.Float)
    eval("1.0e100f", "1.0E100", JavaBoxed.Float)
    eval(".1f", "0.1", JavaBoxed.Float)
  }

  @Test
  def testStringLiterals(): Unit = {
    eval(" \"ala\" ", "ala", JavaBoxed.String)
    eval(" \"\"\"ala\"\"\" ", "ala", JavaBoxed.String)
  }

  @Test
  def testTupleLiterals(): Unit = {
    eval(""" (1, 2) """, "(1,2)", "scala.Tuple2")
    eval(""" (1, 2, 3) """, "(1,2,3)", "scala.Tuple3")
    eval(""" (1, 2, 3, 4) """, "(1,2,3,4)", "scala.Tuple4")
  }

  @Test
  def testSymbols(): Unit = eval("'ala", "'ala", "scala.Symbol")
}

object LiteralsTest extends BaseIntegrationTestCompanion
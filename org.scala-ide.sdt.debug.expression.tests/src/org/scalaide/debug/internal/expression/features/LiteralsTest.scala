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

class LiteralsTest extends BaseIntegrationTest(LiteralsTest) {

  @Test
  def testNullLiteral(): Unit = eval("null", Scala.nullLiteral, Scala.nullType)

  @Test
  def testNullLocalValue(): Unit = {
    eval("nullVal", Scala.nullLiteral, Scala.nullType)
    eval("nullValString", Scala.nullLiteral, Scala.nullType)
    eval("nullValArray", Scala.nullLiteral, Scala.nullType)
  }

  @Test
  def testNullReturningFields(): Unit = {
    eval("Libs.nullVal", Scala.nullLiteral, Scala.nullType)
    eval("Libs.nullValString", Scala.nullLiteral, Scala.nullType)
    eval("Libs.nullValArray", Scala.nullLiteral, Scala.nullType)
  }

  @Test
  def testNullReturningMethods(): Unit = {
    eval("Libs.nullDef", Scala.nullLiteral, Scala.nullType)
    eval("Libs.nullDefString", Scala.nullLiteral, Scala.nullType)
    eval("Libs.nullDefArray", Scala.nullLiteral, Scala.nullType)
  }

  @Test
  def testMethodTakingNull(): Unit = {
    eval("Libs.nullCheck(null)", "true", Java.boxed.Boolean)
    eval("Libs.nullCheck(Libs.nullDef)", "true", Java.boxed.Boolean)
    eval("Libs.nullCheck(Libs.nullDefString)", "true", Java.boxed.Boolean)
    eval("Libs.nullCheck(Libs.nullDefArray)", "true", Java.boxed.Boolean)
  }

  @Test
  def testUnitLiteral(): Unit = eval("()", "()", Scala.unitType)

  @Test
  def testIntegerLiterals(): Unit = {
    eval("1", "1", Java.boxed.Integer)
    eval("35", "35", Java.boxed.Integer)
    eval("0xFFFFFFFF", "-1", Java.boxed.Integer)
  }

  @Test
  def testLongLiterals(): Unit = {
    eval("1L", "1", Java.boxed.Long)
    eval("123456789L", "123456789", Java.boxed.Long)
  }

  @Test
  def testDoubleLiterals(): Unit = {
    eval("1.0", "1.0", Java.boxed.Double)
    eval("1e30", "1.0E30", Java.boxed.Double)
    eval("3.14159", "3.14159", Java.boxed.Double)
    eval("1.0e100", "1.0E100", Java.boxed.Double)
    eval(".1", "0.1", Java.boxed.Double)

    eval("1.0d", "1.0", Java.boxed.Double)
    eval("1e30d", "1.0E30", Java.boxed.Double)
    eval("3.14159d", "3.14159", Java.boxed.Double)
    eval("1.0e100d", "1.0E100", Java.boxed.Double)
    eval(".1d", "0.1", Java.boxed.Double)
  }

  @Test
  def testBooleanLiterals(): Unit = {
    eval("false", "false", Java.boxed.Boolean)
    eval("true", "true", Java.boxed.Boolean)
  }

  @Test
  def testCharacterLiterals(): Unit = {
    eval("'c'", "c", Java.boxed.Character)
    eval("'\u0041'", "A", Java.boxed.Character)
    eval("'\t'", "\t", Java.boxed.Character)
  }

  @Test
  def testFloatLiterals(): Unit = {
    eval("1.0f", "1.0", Java.boxed.Float)
    eval("1e30f", "1.0E30", Java.boxed.Float)
    eval("3.14159f", "3.14159", Java.boxed.Float)
    eval("1.0e10f", "1.0E10", Java.boxed.Float)
    eval(".1f", "0.1", Java.boxed.Float)
  }

  @Test
  def testStringLiteralsWithTrailingWhitespace(): Unit = {
    eval(s(""), s(""), Java.boxed.String)
    eval(s(" "), s(" "), Java.boxed.String)
    eval(s(" a"), s(" a"), Java.boxed.String)
    eval(s("a "), s("a "), Java.boxed.String)
    eval(s(" a "), s(" a "), Java.boxed.String)
  }

  @Test
  def testStringLiterals(): Unit = {
    eval(" \"ala\" ", "ala", Java.boxed.String)
    eval(" \"\"\"ala\"\"\" ", "ala", Java.boxed.String)
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

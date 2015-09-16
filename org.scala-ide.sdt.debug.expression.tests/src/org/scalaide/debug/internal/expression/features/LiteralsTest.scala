/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class LiteralsTest extends BaseIntegrationTest(LiteralsTest) {

  @Test
  def testNullLiteral(): Unit = eval("null", null, Scala.nullType)

  @Test
  def testNullLocalValue(): Unit = {
    eval("nullVal", null, Scala.nullType)
    eval("nullValString", null, Scala.nullType)
    eval("nullValArray", null, Scala.nullType)
  }

  @Test
  def testNullReturningFields(): Unit = {
    eval("Libs.nullVal", null, Scala.nullType)
    eval("Libs.nullValString", null, Scala.nullType)
    eval("Libs.nullValArray", null, Scala.nullType)
  }

  @Test
  def testNullReturningMethods(): Unit = {
    eval("Libs.nullDef", null, Scala.nullType)
    eval("Libs.nullDefString", null, Scala.nullType)
    eval("Libs.nullDefArray", null, Scala.nullType)
  }

  @Test
  def testMethodTakingNull(): Unit = {
    eval("Libs.nullCheck(null)", true, Java.primitives.boolean)
    eval("Libs.nullCheck(Libs.nullDef)", true, Java.primitives.boolean)
    eval("Libs.nullCheck(Libs.nullDefString)", true, Java.primitives.boolean)
    eval("Libs.nullCheck(Libs.nullDefArray)", true, Java.primitives.boolean)
  }

  @Test
  def testUnitLiteral(): Unit = eval("()", (), Scala.unitType)

  @Test
  def testIntegerLiterals(): Unit = {
    eval("1", 1, Java.primitives.int)
    eval("35", 35, Java.primitives.int)
    eval("0xFFFFFFFF", -1, Java.primitives.int)
  }

  @Test
  def testLongLiterals(): Unit = {
    eval("1L", 1L, Java.primitives.long)
    eval("123456789L", 123456789L, Java.primitives.long)
  }

  @Test
  def testDoubleLiterals(): Unit = {
    eval("1.0", 1.0, Java.primitives.double)
    eval("1e30", 1.0E30, Java.primitives.double)
    eval("3.14159", 3.14159, Java.primitives.double)
    eval("1.0e100", 1.0E100, Java.primitives.double)
    eval(".1", 0.1, Java.primitives.double)

    eval("1.0d", 1.0, Java.primitives.double)
    eval("1e30d", 1.0E30, Java.primitives.double)
    eval("3.14159d", 3.14159, Java.primitives.double)
    eval("1.0e100d", 1.0E100, Java.primitives.double)
    eval(".1d", 0.1, Java.primitives.double)
  }

  @Test
  def testBooleanLiterals(): Unit = {
    eval("false", false, Java.primitives.boolean)
    eval("true", true, Java.primitives.boolean)
  }

  @Test
  def testCharacterLiterals(): Unit = {
    eval("'c'", 'c', Java.primitives.char)
    eval("'\u0041'", 'A', Java.primitives.char)
    eval("'\t'", '\t', Java.primitives.char)
  }

  @Test
  def testFloatLiterals(): Unit = {
    eval("1.0f", 1.0, Java.primitives.float)
    eval("1e30f", 1.0E30, Java.primitives.float)
    eval("3.14159f", 3.14159, Java.primitives.float)
    eval("1.0e10f", 1.0E10, Java.primitives.float)
    eval(".1f", 0.1, Java.primitives.float)
  }

  @Test
  def testStringLiteralsWithTrailingWhitespace(): Unit = {
    eval(s(""), s(""), Java.String)
    eval(s(" "), s(" "), Java.String)
    eval(s(" a"), s(" a"), Java.String)
    eval(s("a "), s("a "), Java.String)
    eval(s(" a "), s(" a "), Java.String)
  }

  @Test
  def testStringLiterals(): Unit = {
    eval(" \"ala\" ", "ala", Java.String)
    eval(" \"\"\"ala\"\"\" ", "ala", Java.String)
  }

  @Test
  def testTupleLiterals(): Unit = {
    eval(""" (1, 2) """, (1,2), "scala.Tuple2")
    eval(""" (1, 2, 3) """, (1,2,3), "scala.Tuple3")
    eval(""" (1, 2, 3, 4) """, (1,2,3,4), "scala.Tuple4")
  }

  @Test
  def testSymbols(): Unit = eval("'ala", "'ala", "scala.Symbol")
}

object LiteralsTest extends BaseIntegrationTestCompanion

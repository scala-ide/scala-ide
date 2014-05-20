/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class RichTypesOperationsTest extends BaseIntegrationTest(RichTypesOperationsTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def testOperationsOnScalaNumberProxy(): Unit = {
    eval("int.isWhole", int.isWhole, JavaBoxed.Boolean)

    eval("int.toChar", int.toChar, JavaBoxed.Character)
    eval("int.toByte", int.toByte, JavaBoxed.Byte)
    eval("int.toShort", int.toShort, JavaBoxed.Short)
    eval("int.toInt", int.toInt, JavaBoxed.Integer)
    eval("int.toLong", int.toLong, JavaBoxed.Long)
    eval("int.toFloat", int.toFloat, JavaBoxed.Float)
    eval("int.toDouble", int.toDouble, JavaBoxed.Double)

    eval("int.isValidByte", int.isValidByte, JavaBoxed.Boolean)
    eval("int.isValidShort", int.isValidShort, JavaBoxed.Boolean)
    eval("int.isValidInt", int.isValidInt, JavaBoxed.Boolean)
    eval("int.isValidChar", int.isValidChar, JavaBoxed.Boolean)

    eval("int.max(int2)", int.max(int2), JavaBoxed.Integer)
    eval("int.min(int2)", int.min(int2), JavaBoxed.Integer)
    eval("int.abs", int.abs, JavaBoxed.Integer)
    eval("int.signum", int.signum, JavaBoxed.Integer)
  }

  @Test
  def testOperationsOnRichChar(): Unit = {
    eval("char.asDigit", char.asDigit, JavaBoxed.Integer)

    eval("char.isControl", char.isControl, JavaBoxed.Boolean)
    eval("char.isDigit", char.isDigit, JavaBoxed.Boolean)
    eval("char.isLetter", char.isLetter, JavaBoxed.Boolean)
    eval("char.isLetterOrDigit", char.isLetterOrDigit, JavaBoxed.Boolean)
    eval("char.isWhitespace", char.isWhitespace, JavaBoxed.Boolean)
    eval("char.isSpaceChar", char.isSpaceChar, JavaBoxed.Boolean)
    eval("char.isHighSurrogate", char.isHighSurrogate, JavaBoxed.Boolean)
    eval("char.isLowSurrogate", char.isLowSurrogate, JavaBoxed.Boolean)
    eval("char.isSurrogate", char.isSurrogate, JavaBoxed.Boolean)
    eval("char.isUnicodeIdentifierStart", char.isUnicodeIdentifierStart, JavaBoxed.Boolean)
    eval("char.isUnicodeIdentifierPart", char.isUnicodeIdentifierPart, JavaBoxed.Boolean)
    eval("char.isIdentifierIgnorable", char.isIdentifierIgnorable, JavaBoxed.Boolean)
    eval("char.isMirrored", char.isMirrored, JavaBoxed.Boolean)

    eval("char.isLower", char.isLower, JavaBoxed.Boolean)
    eval("char.isUpper", char.isUpper, JavaBoxed.Boolean)
    eval("char.isTitleCase", char.isTitleCase, JavaBoxed.Boolean)

    eval("char.toLower", char.toLower, JavaBoxed.Character)
    eval("char.toUpper", char.toUpper, JavaBoxed.Character)
    eval("char.toTitleCase", char.toTitleCase, JavaBoxed.Character)

    eval("char.getType", char.getType, JavaBoxed.Integer)
    eval("char.getNumericValue", char.getNumericValue, JavaBoxed.Integer)
    eval("char.getDirectionality", char.getDirectionality, JavaBoxed.Byte)
    eval("char.reverseBytes", char.reverseBytes, JavaBoxed.Character)
  }

  @Test
  def testRangeOperationsOnIntegral(): Unit = {
    eval("int to int2", int to int2, ScalaOther.rangeInclusive)
    eval("int.to(int2, 1)", int.to(int2, 1), ScalaOther.rangeInclusive)
    eval("int until int2 mkString", int until int2 mkString, JavaBoxed.String)
    eval("int.until(int2, 1).mkString", int.until(int2, 1).mkString, JavaBoxed.String)
  }

  @Test
  def testRangeOperationsOnFractional(): Unit = {
    eval("double to double2 by(1) mkString", double to double2 by(1) mkString, JavaBoxed.String)
    eval("double.to(double2, 0.5).mkString", double.to(double2, 0.5).mkString, JavaBoxed.String)
    eval("double until double2 by(1) mkString", double until double2 by(1) mkString, JavaBoxed.String)
    eval("double.until(double2, 0.5).mkString", double.until(double2, 0.5).mkString, JavaBoxed.String)
  }

  @Test
  def testOperationsOnRichDouble(): Unit = {
    eval("double.round", double.round, JavaBoxed.Long)
    eval("double.ceil", double.ceil, JavaBoxed.Double)
    eval("double.floor", double.floor, JavaBoxed.Double)

    eval("double.toRadians", double.toRadians, JavaBoxed.Double)
    eval("double.toDegrees", double.toDegrees, JavaBoxed.Double)

    eval("double.isInfinity", double.isInfinity, JavaBoxed.Boolean)
    eval("double.isPosInfinity", double.isPosInfinity, JavaBoxed.Boolean)
    eval("double.isNegInfinity", double.isNegInfinity, JavaBoxed.Boolean)
  }

  @Test
  def testOperationsOnRichFloat(): Unit = {
    eval("float.round", float.round, JavaBoxed.Integer)
    eval("float.ceil", float.ceil, JavaBoxed.Float)
    eval("float.floor", float.floor, JavaBoxed.Float)

    eval("float.toRadians", float.toRadians, JavaBoxed.Float)
    eval("float.toDegrees", float.toDegrees, JavaBoxed.Float)

    eval("float.isInfinity", float.isInfinity, JavaBoxed.Boolean)
    eval("float.isPosInfinity", float.isPosInfinity, JavaBoxed.Boolean)
    eval("float.isNegInfinity", float.isNegInfinity, JavaBoxed.Boolean)
  }

  @Test
  def testOperationsOnRichInt(): Unit = {
    eval("int.toBinaryString", int.toBinaryString, JavaBoxed.String)
    eval("int.toHexString", int.toHexString, JavaBoxed.String)
    eval("int.toOctalString", int.toOctalString, JavaBoxed.String)
  }

  @Test
  def testOperationsOnRichLong(): Unit = {
    eval("long.toBinaryString", long.toBinaryString, JavaBoxed.String)
    eval("long.toHexString", long.toHexString, JavaBoxed.String)
    eval("long.toOctalString", long.toOctalString, JavaBoxed.String)
  }
}

object RichTypesOperationsTest extends BaseIntegrationTestCompanion

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class RichTypesOperationsTest extends BaseIntegrationTest(RichTypesOperationsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def testOperationsOnScalaNumberProxy(): Unit = {
    eval("int.isWhole", int.isWhole, Java.boxed.Boolean)

    eval("int.byteValue", int.byteValue, Java.boxed.Byte)
    eval("int.doubleValue", int.doubleValue, Java.boxed.Double)
    eval("int.floatValue", int.floatValue, Java.boxed.Float)
    eval("int.intValue", int.intValue, Java.boxed.Integer)
    eval("int.longValue", int.longValue, Java.boxed.Long)
    eval("int.shortValue", int.shortValue, Java.boxed.Short)

    eval("int.toChar", int.toChar, Java.boxed.Character)
    eval("int.toByte", int.toByte, Java.boxed.Byte)
    eval("int.toShort", int.toShort, Java.boxed.Short)
    eval("int.toInt", int.toInt, Java.boxed.Integer)
    eval("int.toLong", int.toLong, Java.boxed.Long)
    eval("int.toFloat", int.toFloat, Java.boxed.Float)
    eval("int.toDouble", int.toDouble, Java.boxed.Double)

    eval("int.isValidByte", int.isValidByte, Java.boxed.Boolean)
    eval("int.isValidShort", int.isValidShort, Java.boxed.Boolean)
    eval("int.isValidInt", int.isValidInt, Java.boxed.Boolean)
    eval("int.isValidChar", int.isValidChar, Java.boxed.Boolean)

    eval("int.max(int2)", int.max(int2), Java.boxed.Integer)
    eval("int.min(int2)", int.min(int2), Java.boxed.Integer)
    eval("int.abs", int.abs, Java.boxed.Integer)
    eval("int.signum", int.signum, Java.boxed.Integer)
  }

  @Test
  def testOperationsOnRichChar(): Unit = {
    eval("char.asDigit", char.asDigit, Java.boxed.Integer)

    eval("char.isControl", char.isControl, Java.boxed.Boolean)
    eval("char.isDigit", char.isDigit, Java.boxed.Boolean)
    eval("char.isLetter", char.isLetter, Java.boxed.Boolean)
    eval("char.isLetterOrDigit", char.isLetterOrDigit, Java.boxed.Boolean)
    eval("char.isWhitespace", char.isWhitespace, Java.boxed.Boolean)
    eval("char.isSpaceChar", char.isSpaceChar, Java.boxed.Boolean)
    eval("char.isHighSurrogate", char.isHighSurrogate, Java.boxed.Boolean)
    eval("char.isLowSurrogate", char.isLowSurrogate, Java.boxed.Boolean)
    eval("char.isSurrogate", char.isSurrogate, Java.boxed.Boolean)
    eval("char.isUnicodeIdentifierStart", char.isUnicodeIdentifierStart, Java.boxed.Boolean)
    eval("char.isUnicodeIdentifierPart", char.isUnicodeIdentifierPart, Java.boxed.Boolean)
    eval("char.isIdentifierIgnorable", char.isIdentifierIgnorable, Java.boxed.Boolean)
    eval("char.isMirrored", char.isMirrored, Java.boxed.Boolean)

    eval("char.isLower", char.isLower, Java.boxed.Boolean)
    eval("char.isUpper", char.isUpper, Java.boxed.Boolean)
    eval("char.isTitleCase", char.isTitleCase, Java.boxed.Boolean)

    eval("char.toLower", char.toLower, Java.boxed.Character)
    eval("char.toUpper", char.toUpper, Java.boxed.Character)
    eval("char.toTitleCase", char.toTitleCase, Java.boxed.Character)

    eval("char.getType", char.getType, Java.boxed.Integer)
    eval("char.getNumericValue", char.getNumericValue, Java.boxed.Integer)
    eval("char.getDirectionality", char.getDirectionality, Java.boxed.Byte)
    eval("char.reverseBytes", char.reverseBytes, Java.boxed.Character)
  }

  @Test
  def testRangeOperationsOnIntegral(): Unit = {
    eval("int to int2", int to int2, Scala.rangeInclusive)
    eval("int.to(int2, 1)", int.to(int2, 1), Scala.rangeInclusive)
    eval("(int until int2).mkString", (int until int2).mkString, Java.boxed.String)
    eval("int.until(int2, 1).mkString", int.until(int2, 1).mkString, Java.boxed.String)
  }

  @Test
  def testRangeOperationsOnFractional(): Unit = {
    eval("(double to double2 by 1).mkString", (double to double2 by 1).mkString, Java.boxed.String)
    eval("double.to(double2, 0.5).mkString", double.to(double2, 0.5).mkString, Java.boxed.String)
    eval("(double until double2 by 1).mkString", (double until double2 by 1).mkString, Java.boxed.String)
    eval("double.until(double2, 0.5).mkString", double.until(double2, 0.5).mkString, Java.boxed.String)
  }

  @Test
  def testOperationsOnRichDouble(): Unit = {
    eval("double.round", double.round, Java.boxed.Long)
    eval("double.ceil", double.ceil, Java.boxed.Double)
    eval("double.floor", double.floor, Java.boxed.Double)

    eval("double.toRadians", double.toRadians, Java.boxed.Double)
    eval("double.toDegrees", double.toDegrees, Java.boxed.Double)

    eval("double.isInfinity", double.isInfinity, Java.boxed.Boolean)
    eval("double.isPosInfinity", double.isPosInfinity, Java.boxed.Boolean)
    eval("double.isNegInfinity", double.isNegInfinity, Java.boxed.Boolean)
  }

  @Test
  def testOperationsOnRichFloat(): Unit = {
    eval("float.round", float.round, Java.boxed.Integer)
    eval("float.ceil", float.ceil, Java.boxed.Float)
    eval("float.floor", float.floor, Java.boxed.Float)

    eval("float.toRadians", float.toRadians, Java.boxed.Float)
    eval("float.toDegrees", float.toDegrees, Java.boxed.Float)

    eval("float.isInfinity", float.isInfinity, Java.boxed.Boolean)
    eval("float.isPosInfinity", float.isPosInfinity, Java.boxed.Boolean)
    eval("float.isNegInfinity", float.isNegInfinity, Java.boxed.Boolean)
  }

  @Test
  def testOperationsOnRichInt(): Unit = {
    eval("int.toBinaryString", int.toBinaryString, Java.boxed.String)
    eval("int.toHexString", int.toHexString, Java.boxed.String)
    eval("int.toOctalString", int.toOctalString, Java.boxed.String)
  }

  @Test
  def testOperationsOnRichLong(): Unit = {
    eval("long.toBinaryString", long.toBinaryString, Java.boxed.String)
    eval("long.toHexString", long.toHexString, Java.boxed.String)
    eval("long.toOctalString", long.toOctalString, Java.boxed.String)
  }
}

object RichTypesOperationsTest extends BaseIntegrationTestCompanion

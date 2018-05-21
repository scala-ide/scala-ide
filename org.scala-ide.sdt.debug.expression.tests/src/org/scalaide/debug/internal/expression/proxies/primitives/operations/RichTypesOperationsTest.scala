/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class RichTypesOperationsTest extends BaseIntegrationTest(RichTypesOperationsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def testOperationsOnScalaNumberProxy(): Unit = {
    eval("int.isWhole", int.isWhole, Java.primitives.boolean)

    eval("int.byteValue", int.byteValue, Java.primitives.byte)
    eval("int.doubleValue", int.doubleValue, Java.primitives.double)
    eval("int.floatValue", int.floatValue, Java.primitives.float)
    eval("int.intValue", int.intValue, Java.primitives.int)
    eval("int.longValue", int.longValue, Java.primitives.long)
    eval("int.shortValue", int.shortValue, Java.primitives.short)

    eval("int.toChar", int.toChar, Java.primitives.char)
    eval("int.toByte", int.toByte, Java.primitives.byte)
    eval("int.toShort", int.toShort, Java.primitives.short)
    eval("int.toInt", int.toInt, Java.primitives.int)
    eval("int.toLong", int.toLong, Java.primitives.long)
    eval("int.toFloat", int.toFloat, Java.primitives.float)
    eval("int.toDouble", int.toDouble, Java.primitives.double)

    eval("int.isValidByte", int.isValidByte, Java.primitives.boolean)
    eval("int.isValidShort", int.isValidShort, Java.primitives.boolean)
    eval("int.isValidInt", int.isValidInt, Java.primitives.boolean)
    eval("int.isValidChar", int.isValidChar, Java.primitives.boolean)

    eval("int.max(int2)", int.max(int2), Java.primitives.int)
    eval("int.min(int2)", int.min(int2), Java.primitives.int)
    eval("int.abs", int.abs, Java.primitives.int)
    eval("int.signum", int.signum, Java.primitives.int)
  }

  @Test
  def testOperationsOnRichChar(): Unit = {
    eval("char.asDigit", char.asDigit, Java.primitives.int)

    eval("char.isControl", char.isControl, Java.primitives.boolean)
    eval("char.isDigit", char.isDigit, Java.primitives.boolean)
    eval("char.isLetter", char.isLetter, Java.primitives.boolean)
    eval("char.isLetterOrDigit", char.isLetterOrDigit, Java.primitives.boolean)
    eval("char.isWhitespace", char.isWhitespace, Java.primitives.boolean)
    eval("char.isSpaceChar", char.isSpaceChar, Java.primitives.boolean)
    eval("char.isHighSurrogate", char.isHighSurrogate, Java.primitives.boolean)
    eval("char.isLowSurrogate", char.isLowSurrogate, Java.primitives.boolean)
    eval("char.isSurrogate", char.isSurrogate, Java.primitives.boolean)
    eval("char.isUnicodeIdentifierStart", char.isUnicodeIdentifierStart, Java.primitives.boolean)
    eval("char.isUnicodeIdentifierPart", char.isUnicodeIdentifierPart, Java.primitives.boolean)
    eval("char.isIdentifierIgnorable", char.isIdentifierIgnorable, Java.primitives.boolean)
    eval("char.isMirrored", char.isMirrored, Java.primitives.boolean)

    eval("char.isLower", char.isLower, Java.primitives.boolean)
    eval("char.isUpper", char.isUpper, Java.primitives.boolean)
    eval("char.isTitleCase", char.isTitleCase, Java.primitives.boolean)

    eval("char.toLower", char.toLower, Java.primitives.char)
    eval("char.toUpper", char.toUpper, Java.primitives.char)
    eval("char.toTitleCase", char.toTitleCase, Java.primitives.char)

    eval("char.getType", char.getType, Java.primitives.int)
    eval("char.getNumericValue", char.getNumericValue, Java.primitives.int)
    eval("char.getDirectionality", char.getDirectionality, Java.primitives.byte)
    eval("char.reverseBytes", char.reverseBytes, Java.primitives.char)
  }

  @Test
  def testRangeOperationsOnIntegral(): Unit = {
    eval("int to int2", int to int2, Scala.rangeInclusive)
    eval("int.to(int2, 1)", int.to(int2, 1), Scala.rangeInclusive)
    eval("(int until int2).mkString", (int until int2).mkString, Java.String)
    eval("int.until(int2, 1).mkString", int.until(int2, 1).mkString, Java.String)
  }

  @Test
  def testRangeOperationsOnFractional(): Unit = {
    eval("(bigDecimal to bigDecimal2 by 1).mkString", (bigDecimal to bigDecimal2 by 1).mkString, Java.String)
    eval("bigDecimal.to(bigDecimal2, 0.5).mkString", bigDecimal.to(bigDecimal2, 0.5).mkString, Java.String)
    eval("(bigDecimal until bigDecimal2 by 1).mkString", (bigDecimal until bigDecimal2 by 1).mkString, Java.String)
    eval("bigDecimal.until(bigDecimal2, 0.5).mkString", bigDecimal.until(bigDecimal2, 0.5).mkString, Java.String)
  }

  @Test
  def testOperationsOnRichDouble(): Unit = {
    eval("double.round", double.round, Java.primitives.long)
    eval("double.ceil", double.ceil, Java.primitives.double)
    eval("double.floor", double.floor, Java.primitives.double)

    eval("double.toRadians", double.toRadians, Java.primitives.double)
    eval("double.toDegrees", double.toDegrees, Java.primitives.double)

    eval("double.isInfinity", double.isInfinity, Java.primitives.boolean)
    eval("double.isPosInfinity", double.isPosInfinity, Java.primitives.boolean)
    eval("double.isNegInfinity", double.isNegInfinity, Java.primitives.boolean)
  }

  @Test
  def testOperationsOnRichFloat(): Unit = {
    eval("float.round", float.round, Java.primitives.int)
    eval("float.ceil", float.ceil, Java.primitives.float)
    eval("float.floor", float.floor, Java.primitives.float)

    eval("float.toRadians", float.toRadians, Java.primitives.float)
    eval("float.toDegrees", float.toDegrees, Java.primitives.float)

    eval("float.isInfinity", float.isInfinity, Java.primitives.boolean)
    eval("float.isPosInfinity", float.isPosInfinity, Java.primitives.boolean)
    eval("float.isNegInfinity", float.isNegInfinity, Java.primitives.boolean)
  }

  @Test
  def testOperationsOnRichInt(): Unit = {
    eval("int.toBinaryString", int.toBinaryString, Java.String)
    eval("int.toHexString", int.toHexString, Java.String)
    eval("int.toOctalString", int.toOctalString, Java.String)
  }

  @Test
  def testOperationsOnRichLong(): Unit = {
    eval("long.toBinaryString", long.toBinaryString, Java.String)
    eval("long.toHexString", long.toHexString, Java.String)
    eval("long.toOctalString", long.toOctalString, Java.String)
  }
}

object RichTypesOperationsTest extends BaseIntegrationTestCompanion

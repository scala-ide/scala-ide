/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.junit.Assert._
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues

class TypeConversionsTest extends BaseIntegrationTest(TypeConversionsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def testByteConversions() {
    eval("byte.toByte", byte.toByte, Java.primitives.byte)
    eval("byte.toShort", byte.toShort, Java.primitives.short)
    eval("byte.toChar", byte.toChar, Java.primitives.char)
    eval("byte.toInt", byte.toInt, Java.primitives.int)
    eval("byte.toLong", byte.toLong, Java.primitives.long)
    eval("byte.toFloat", byte.toFloat, Java.primitives.float)
    eval("byte.toDouble", byte.toDouble, Java.primitives.double)
  }

  @Test
  def testShortConversions() {
    eval("short.toByte", short.toByte, Java.primitives.byte)
    eval("short.toShort", short.toShort, Java.primitives.short)
    eval("short.toChar", short.toChar, Java.primitives.char)
    eval("short.toInt", short.toInt, Java.primitives.int)
    eval("short.toLong", short.toLong, Java.primitives.long)
    eval("short.toFloat", short.toFloat, Java.primitives.float)
    eval("short.toDouble", short.toDouble, Java.primitives.double)
  }

  @Test
  def testCharConversions() {
    eval("char.toByte", char.toByte, Java.primitives.byte)
    eval("char.toShort", char.toShort, Java.primitives.short)
    eval("char.toChar", char.toChar, Java.primitives.char)
    eval("char.toInt", char.toInt, Java.primitives.int)
    eval("char.toLong", char.toLong, Java.primitives.long)
    eval("char.toFloat", char.toFloat, Java.primitives.float)
    eval("char.toDouble", char.toDouble, Java.primitives.double)
  }

  @Test
  def testIntConversions() {
    eval("int.toByte", int.toByte, Java.primitives.byte)
    eval("int.toShort", int.toShort, Java.primitives.short)
    eval("int.toChar", int.toChar, Java.primitives.char)
    eval("int.toInt", int.toInt, Java.primitives.int)
    eval("int.toLong", int.toLong, Java.primitives.long)
    eval("int.toFloat", int.toFloat, Java.primitives.float)
    eval("int.toDouble", int.toDouble, Java.primitives.double)
  }

  @Test
  def testLongConversions() {
    eval("long.toByte", long.toByte, Java.primitives.byte)
    eval("long.toShort", long.toShort, Java.primitives.short)
    eval("long.toChar", long.toChar, Java.primitives.char)
    eval("long.toInt", long.toInt, Java.primitives.int)
    eval("long.toLong", long.toLong, Java.primitives.long)
    eval("long.toFloat", long.toFloat, Java.primitives.float)
    eval("long.toDouble", long.toDouble, Java.primitives.double)
  }

  @Test
  def testFloatConversions() {
    eval("float.toByte", float.toByte, Java.primitives.byte)
    eval("float.toShort", float.toShort, Java.primitives.short)
    eval("float.toChar", float.toChar, Java.primitives.char)
    eval("float.toInt", float.toInt, Java.primitives.int)
    eval("float.toLong", float.toLong, Java.primitives.long)
    eval("float.toFloat", float.toFloat, Java.primitives.float)
    eval("float.toDouble", float.toDouble, Java.primitives.double)
  }

  @Test
  def testDoubleConversions() {
    eval("double.toByte", double.toByte, Java.primitives.byte)
    eval("double.toShort", double.toShort, Java.primitives.short)
    eval("double.toChar", double.toChar, Java.primitives.char)
    eval("double.toInt", double.toInt, Java.primitives.int)
    eval("double.toLong", double.toLong, Java.primitives.long)
    eval("double.toFloat", double.toFloat, Java.primitives.float)
    eval("double.toDouble", double.toDouble, Java.primitives.double)
  }
}

object TypeConversionsTest extends BaseIntegrationTestCompanion

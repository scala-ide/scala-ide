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

  import TestValues.any2String
  import TestValues.ValuesTestCase._

  @Test
  def testByteConversions() {
    eval("byte.toByte", byte.toByte, Java.boxed.Byte)
    eval("byte.toShort", byte.toShort, Java.boxed.Short)
    eval("byte.toChar", byte.toChar, Java.boxed.Character)
    eval("byte.toInt", byte.toInt, Java.boxed.Integer)
    eval("byte.toLong", byte.toLong, Java.boxed.Long)
    eval("byte.toFloat", byte.toFloat, Java.boxed.Float)
    eval("byte.toDouble", byte.toDouble, Java.boxed.Double)
  }

  @Test
  def testShortConversions() {
    eval("short.toByte", short.toByte, Java.boxed.Byte)
    eval("short.toShort", short.toShort, Java.boxed.Short)
    eval("short.toChar", short.toChar, Java.boxed.Character)
    eval("short.toInt", short.toInt, Java.boxed.Integer)
    eval("short.toLong", short.toLong, Java.boxed.Long)
    eval("short.toFloat", short.toFloat, Java.boxed.Float)
    eval("short.toDouble", short.toDouble, Java.boxed.Double)
  }

  @Test
  def testCharConversions() {
    eval("char.toByte", char.toByte, Java.boxed.Byte)
    eval("char.toShort", char.toShort, Java.boxed.Short)
    eval("char.toChar", char.toChar, Java.boxed.Character)
    eval("char.toInt", char.toInt, Java.boxed.Integer)
    eval("char.toLong", char.toLong, Java.boxed.Long)
    eval("char.toFloat", char.toFloat, Java.boxed.Float)
    eval("char.toDouble", char.toDouble, Java.boxed.Double)
  }

  @Test
  def testIntConversions() {
    eval("int.toByte", int.toByte, Java.boxed.Byte)
    eval("int.toShort", int.toShort, Java.boxed.Short)
    eval("int.toChar", int.toChar, Java.boxed.Character)
    eval("int.toInt", int.toInt, Java.boxed.Integer)
    eval("int.toLong", int.toLong, Java.boxed.Long)
    eval("int.toFloat", int.toFloat, Java.boxed.Float)
    eval("int.toDouble", int.toDouble, Java.boxed.Double)
  }

  @Test
  def testLongConversions() {
    eval("long.toByte", long.toByte, Java.boxed.Byte)
    eval("long.toShort", long.toShort, Java.boxed.Short)
    eval("long.toChar", long.toChar, Java.boxed.Character)
    eval("long.toInt", long.toInt, Java.boxed.Integer)
    eval("long.toLong", long.toLong, Java.boxed.Long)
    eval("long.toFloat", long.toFloat, Java.boxed.Float)
    eval("long.toDouble", long.toDouble, Java.boxed.Double)
  }

  @Test
  def testFloatConversions() {
    eval("float.toByte", float.toByte, Java.boxed.Byte)
    eval("float.toShort", float.toShort, Java.boxed.Short)
    eval("float.toChar", float.toChar, Java.boxed.Character)
    eval("float.toInt", float.toInt, Java.boxed.Integer)
    eval("float.toLong", float.toLong, Java.boxed.Long)
    eval("float.toFloat", float.toFloat, Java.boxed.Float)
    eval("float.toDouble", float.toDouble, Java.boxed.Double)
  }

  @Test
  def testDoubleConversions() {
    eval("double.toByte", double.toByte, Java.boxed.Byte)
    eval("double.toShort", double.toShort, Java.boxed.Short)
    eval("double.toChar", double.toChar, Java.boxed.Character)
    eval("double.toInt", double.toInt, Java.boxed.Integer)
    eval("double.toLong", double.toLong, Java.boxed.Long)
    eval("double.toFloat", double.toFloat, Java.boxed.Float)
    eval("double.toDouble", double.toDouble, Java.boxed.Double)
  }
}

object TypeConversionsTest extends BaseIntegrationTestCompanion

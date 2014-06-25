/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.junit.Assert._
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

class TypeConversionsTest extends BaseIntegrationTest(TypeConversionsTest) {

  @Test
  def testByteConversions() {
    implicit val context = createContext()
    val byte: Byte = 1
    val byteProxy = ByteJdiProxy.fromPrimitive(byte, context)
    compareProxies(byteProxy.toByte, byteProxy)
    compareProxies(byteProxy.toShort, ShortJdiProxy.fromPrimitive(byte.toShort, context))
    compareProxies(byteProxy.toChar, CharJdiProxy.fromPrimitive(byte.toChar, context))
    compareProxies(byteProxy.toInt, IntJdiProxy.fromPrimitive(byte.toInt, context))
    compareProxies(byteProxy.toLong, LongJdiProxy.fromPrimitive(byte.toLong, context))
    compareProxies(byteProxy.toFloat, FloatJdiProxy.fromPrimitive(byte.toFloat, context))
    compareProxies(byteProxy.toDouble, DoubleJdiProxy.fromPrimitive(byte.toDouble, context))
  }

  @Test
  def testShortConversions() {
    implicit val context = createContext()
    val short: Short = 1
    val shortProxy = ShortJdiProxy.fromPrimitive(short, context)
    compareProxies(shortProxy.toByte, ByteJdiProxy.fromPrimitive(short.toByte, context))
    compareProxies(shortProxy.toShort, shortProxy)
    compareProxies(shortProxy.toChar, CharJdiProxy.fromPrimitive(short.toChar, context))
    compareProxies(shortProxy.toInt, IntJdiProxy.fromPrimitive(short.toInt, context))
    compareProxies(shortProxy.toLong, LongJdiProxy.fromPrimitive(short.toLong, context))
    compareProxies(shortProxy.toFloat, FloatJdiProxy.fromPrimitive(short.toFloat, context))
    compareProxies(shortProxy.toDouble, DoubleJdiProxy.fromPrimitive(short.toDouble, context))
  }

  @Test
  def testCharConversions() {
    implicit val context = createContext()
    val char = 'c'
    val charProxy = CharJdiProxy.fromPrimitive(char, context)
    compareProxies(charProxy.toByte, ByteJdiProxy.fromPrimitive(char.toByte, context))
    compareProxies(charProxy.toShort, ShortJdiProxy.fromPrimitive(char.toShort, context))
    compareProxies(charProxy.toChar, charProxy)
    compareProxies(charProxy.toInt, IntJdiProxy.fromPrimitive(char.toInt, context))
    compareProxies(charProxy.toLong, LongJdiProxy.fromPrimitive(char.toLong, context))
    compareProxies(charProxy.toFloat, FloatJdiProxy.fromPrimitive(char.toFloat, context))
    compareProxies(charProxy.toDouble, DoubleJdiProxy.fromPrimitive(char.toDouble, context))
  }

  @Test
  def testIntConversions() {
    implicit val context = createContext()
    val int = 2
    val intProxy = IntJdiProxy.fromPrimitive(int, context)
    compareProxies(intProxy.toByte, ByteJdiProxy.fromPrimitive(int.toByte, context))
    compareProxies(intProxy.toShort, ShortJdiProxy.fromPrimitive(int.toShort, context))
    compareProxies(intProxy.toChar, CharJdiProxy.fromPrimitive(int.toChar, context))
    compareProxies(intProxy.toInt, intProxy)
    compareProxies(intProxy.toLong, LongJdiProxy.fromPrimitive(int.toLong, context))
    compareProxies(intProxy.toFloat, FloatJdiProxy.fromPrimitive(int.toFloat, context))
    compareProxies(intProxy.toDouble, DoubleJdiProxy.fromPrimitive(int.toDouble, context))
  }

  @Test
  def testLongConversions() {
    implicit val context = createContext()
    val long = 4L
    val longProxy = LongJdiProxy.fromPrimitive(long, context)
    compareProxies(longProxy.toByte, ByteJdiProxy.fromPrimitive(long.toByte, context))
    compareProxies(longProxy.toShort, ShortJdiProxy.fromPrimitive(long.toShort, context))
    compareProxies(longProxy.toChar, CharJdiProxy.fromPrimitive(long.toChar, context))
    compareProxies(longProxy.toInt, IntJdiProxy.fromPrimitive(long.toInt, context))
    compareProxies(longProxy.toLong, longProxy)
    compareProxies(longProxy.toFloat, FloatJdiProxy.fromPrimitive(long.toFloat, context))
    compareProxies(longProxy.toDouble, DoubleJdiProxy.fromPrimitive(long.toDouble, context))
  }

  @Test
  def testFloatConversions() {
    implicit val context = createContext()
    val float = 3.5f
    val floatProxy = FloatJdiProxy.fromPrimitive(float, context)
    compareProxies(floatProxy.toByte, ByteJdiProxy.fromPrimitive(float.toByte, context))
    compareProxies(floatProxy.toShort, ShortJdiProxy.fromPrimitive(float.toShort, context))
    compareProxies(floatProxy.toChar, CharJdiProxy.fromPrimitive(float.toChar, context))
    compareProxies(floatProxy.toInt, IntJdiProxy.fromPrimitive(float.toInt, context))
    compareProxies(floatProxy.toLong, LongJdiProxy.fromPrimitive(float.toLong, context))
    compareProxies(floatProxy.toFloat, floatProxy)
    compareProxies(floatProxy.toDouble, DoubleJdiProxy.fromPrimitive(float.toDouble, context))
  }

  @Test
  def testDoubleConversions() {
    implicit val context = createContext()
    val double = 3.5
    val doubleProxy = DoubleJdiProxy.fromPrimitive(double, context)
    compareProxies(doubleProxy.toByte, ByteJdiProxy.fromPrimitive(double.toByte, context))
    compareProxies(doubleProxy.toShort, ShortJdiProxy.fromPrimitive(double.toShort, context))
    compareProxies(doubleProxy.toChar, CharJdiProxy.fromPrimitive(double.toChar, context))
    compareProxies(doubleProxy.toInt, IntJdiProxy.fromPrimitive(double.toInt, context))
    compareProxies(doubleProxy.toLong, LongJdiProxy.fromPrimitive(double.toLong, context))
    compareProxies(doubleProxy.toFloat, FloatJdiProxy.fromPrimitive(double.toFloat, context))
    compareProxies(doubleProxy.toDouble, doubleProxy)
  }

  private def createContext() = companion.expressionEvaluator.createContext()

  private def compareProxies(actual: JdiProxy, expected: JdiProxy)(implicit context: JdiContext) {
    assertEquals("Result JDIProxies differs:", context.show(expected), context.show(actual))
  }
}

object TypeConversionsTest extends BaseIntegrationTestCompanion

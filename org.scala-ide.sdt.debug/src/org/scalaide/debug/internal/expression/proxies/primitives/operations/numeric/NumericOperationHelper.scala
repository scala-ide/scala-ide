/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatingPointNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy

import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue

/**
 * Provides general way of handling operations for primitive proxies.
 */
abstract class NumericOperationHelper(protected val context: JdiContext) {

  protected def byteWithByteFun(a: Byte, b: Byte): IntJdiProxy
  protected def shortWithByteFun(a: Short, b: Byte): IntJdiProxy
  protected def charWithByteFun(a: Char, b: Byte): IntJdiProxy
  protected def intWithByteFun(a: Int, b: Byte): IntJdiProxy
  protected def floatWithByteFun(a: Float, b: Byte): FloatJdiProxy
  protected def doubleWithByteFun(a: Double, b: Byte): DoubleJdiProxy
  protected def longWithByteFun(a: Long, b: Byte): LongJdiProxy

  protected def byteWithShortFun(a: Byte, b: Short): IntJdiProxy
  protected def shortWithShortFun(a: Short, b: Short): IntJdiProxy
  protected def charWithShortFun(a: Char, b: Short): IntJdiProxy
  protected def intWithShortFun(a: Int, b: Short): IntJdiProxy
  protected def floatWithShortFun(a: Float, b: Short): FloatJdiProxy
  protected def doubleWithShortFun(a: Double, b: Short): DoubleJdiProxy
  protected def longWithShortFun(a: Long, b: Short): LongJdiProxy

  protected def byteWithCharFun(a: Byte, b: Char): IntJdiProxy
  protected def shortWithCharFun(a: Short, b: Char): IntJdiProxy
  protected def charWithCharFun(a: Char, b: Char): IntJdiProxy
  protected def intWithCharFun(a: Int, b: Char): IntJdiProxy
  protected def floatWithCharFun(a: Float, b: Char): FloatJdiProxy
  protected def doubleWithCharFun(a: Double, b: Char): DoubleJdiProxy
  protected def longWithCharFun(a: Long, b: Char): LongJdiProxy

  protected def byteWithIntFun(a: Byte, b: Int): IntJdiProxy
  protected def shortWithIntFun(a: Short, b: Int): IntJdiProxy
  protected def charWithIntFun(a: Char, b: Int): IntJdiProxy
  protected def intWithIntFun(a: Int, b: Int): IntJdiProxy
  protected def floatWithIntFun(a: Float, b: Int): FloatJdiProxy
  protected def doubleWithIntFun(a: Double, b: Int): DoubleJdiProxy
  protected def longWithIntFun(a: Long, b: Int): LongJdiProxy

  protected def byteWithLongFun(a: Byte, b: Long): LongJdiProxy
  protected def shortWithLongFun(a: Short, b: Long): LongJdiProxy
  protected def charWithLongFun(a: Char, b: Long): LongJdiProxy
  protected def intWithLongFun(a: Int, b: Long): LongJdiProxy
  protected def floatWithLongFun(a: Float, b: Long): FloatJdiProxy
  protected def doubleWithLongFun(a: Double, b: Long): DoubleJdiProxy
  protected def longWithLongFun(a: Long, b: Long): LongJdiProxy

  protected def byteWithFloatFun(a: Byte, b: Float): FloatJdiProxy
  protected def shortWithFloatFun(a: Short, b: Float): FloatJdiProxy
  protected def charWithFloatFun(a: Char, b: Float): FloatJdiProxy
  protected def intWithFloatFun(a: Int, b: Float): FloatJdiProxy
  protected def floatWithFloatFun(a: Float, b: Float): FloatJdiProxy
  protected def doubleWithFloatFun(a: Double, b: Float): DoubleJdiProxy
  protected def longWithFloatFun(a: Long, b: Float): FloatJdiProxy

  protected def byteWithDoubleFun(a: Byte, b: Double): DoubleJdiProxy
  protected def shortWithDoubleFun(a: Short, b: Double): DoubleJdiProxy
  protected def charWithDoubleFun(a: Char, b: Double): DoubleJdiProxy
  protected def intWithDoubleFun(a: Int, b: Double): DoubleJdiProxy
  protected def floatWithDoubleFun(a: Float, b: Double): DoubleJdiProxy
  protected def doubleWithDoubleFun(a: Double, b: Double): DoubleJdiProxy
  protected def longWithDoubleFun(a: Long, b: Double): DoubleJdiProxy

  def applyFloatingPointOperation(self: NumberJdiProxy[_, _], other: NumberJdiProxy[_, _]): FloatingPointNumberJdiProxy[_, _] =
    other.primitive match {
      case b: ByteValue =>
        self.primitive match {
          case a: FloatValue => floatWithByteFun(a.value(), b.value())
          case a: DoubleValue => doubleWithByteFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: ShortValue =>
        self.primitive match {
          case a: FloatValue => floatWithShortFun(a.value(), b.value())
          case a: DoubleValue => doubleWithShortFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: CharValue =>
        self.primitive match {
          case a: FloatValue => floatWithCharFun(a.value(), b.value())
          case a: DoubleValue => doubleWithCharFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: IntegerValue =>
        self.primitive match {
          case a: FloatValue => floatWithIntFun(a.value(), b.value())
          case a: DoubleValue => doubleWithIntFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: LongValue =>
        self.primitive match {
          case a: FloatValue => floatWithLongFun(a.value(), b.value())
          case a: DoubleValue => doubleWithLongFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: FloatValue =>
        self.primitive match {
          case a: ByteValue => byteWithFloatFun(a.value(), b.value())
          case a: ShortValue => shortWithFloatFun(a.value(), b.value())
          case a: CharValue => charWithFloatFun(a.value(), b.value())
          case a: IntegerValue => intWithFloatFun(a.value(), b.value())
          case a: FloatValue => floatWithFloatFun(a.value(), b.value())
          case a: DoubleValue => doubleWithFloatFun(a.value(), b.value())
          case a: LongValue => longWithFloatFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: DoubleValue =>
        self.primitive match {
          case a: ByteValue => byteWithDoubleFun(a.value(), b.value())
          case a: ShortValue => shortWithDoubleFun(a.value(), b.value())
          case a: CharValue => charWithDoubleFun(a.value(), b.value())
          case a: IntegerValue => intWithDoubleFun(a.value(), b.value())
          case a: FloatValue => floatWithDoubleFun(a.value(), b.value())
          case a: DoubleValue => doubleWithDoubleFun(a.value(), b.value())
          case a: LongValue => longWithDoubleFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b => handleUnknown(b)
    }

  def applyIntegerOperation(self: NumberJdiProxy[_, _], other: NumberJdiProxy[_, _]): NumberJdiProxy[_, _] =
    other.primitive match {
      case b: ByteValue =>
        self.primitive match {
          case a: ByteValue => byteWithByteFun(a.value(), b.value())
          case a: ShortValue => shortWithByteFun(a.value(), b.value())
          case a: CharValue => charWithByteFun(a.value(), b.value())
          case a: IntegerValue => intWithByteFun(a.value(), b.value())
          case a: LongValue => longWithByteFun(a.value(), b.value())
          case a: FloatValue => floatWithByteFun(a.value(), b.value())
          case a: DoubleValue => doubleWithByteFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: ShortValue =>
        self.primitive match {
          case a: ByteValue => byteWithShortFun(a.value(), b.value())
          case a: ShortValue => shortWithShortFun(a.value(), b.value())
          case a: CharValue => charWithShortFun(a.value(), b.value())
          case a: IntegerValue => intWithShortFun(a.value(), b.value())
          case a: LongValue => longWithShortFun(a.value(), b.value())
          case a: FloatValue => floatWithShortFun(a.value(), b.value())
          case a: DoubleValue => doubleWithShortFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: CharValue =>
        self.primitive match {
          case a: ByteValue => byteWithCharFun(a.value(), b.value())
          case a: ShortValue => shortWithCharFun(a.value(), b.value())
          case a: CharValue => charWithCharFun(a.value(), b.value())
          case a: IntegerValue => intWithCharFun(a.value(), b.value())
          case a: LongValue => longWithCharFun(a.value(), b.value())
          case a: FloatValue => floatWithCharFun(a.value(), b.value())
          case a: DoubleValue => doubleWithCharFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: IntegerValue =>
        self.primitive match {
          case a: ByteValue => byteWithIntFun(a.value(), b.value())
          case a: ShortValue => shortWithIntFun(a.value(), b.value())
          case a: CharValue => charWithIntFun(a.value(), b.value())
          case a: IntegerValue => intWithIntFun(a.value(), b.value())
          case a: LongValue => longWithIntFun(a.value(), b.value())
          case a: FloatValue => floatWithIntFun(a.value(), b.value())
          case a: DoubleValue => doubleWithIntFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: LongValue =>
        self.primitive match {
          case a: ByteValue => byteWithLongFun(a.value(), b.value())
          case a: ShortValue => shortWithLongFun(a.value(), b.value())
          case a: CharValue => charWithLongFun(a.value(), b.value())
          case a: IntegerValue => intWithLongFun(a.value(), b.value())
          case a: LongValue => longWithLongFun(a.value(), b.value())
          case a: FloatValue => floatWithLongFun(a.value(), b.value())
          case a: DoubleValue => doubleWithLongFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: FloatValue =>
        self.primitive match {
          case a: ByteValue => byteWithFloatFun(a.value(), b.value())
          case a: ShortValue => shortWithFloatFun(a.value(), b.value())
          case a: CharValue => charWithFloatFun(a.value(), b.value())
          case a: IntegerValue => intWithFloatFun(a.value(), b.value())
          case a: FloatValue => floatWithFloatFun(a.value(), b.value())
          case a: DoubleValue => doubleWithFloatFun(a.value(), b.value())
          case a: LongValue => longWithFloatFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: DoubleValue =>
        self.primitive match {
          case a: ByteValue => byteWithDoubleFun(a.value(), b.value())
          case a: ShortValue => shortWithDoubleFun(a.value(), b.value())
          case a: CharValue => charWithDoubleFun(a.value(), b.value())
          case a: IntegerValue => intWithDoubleFun(a.value(), b.value())
          case a: FloatValue => floatWithDoubleFun(a.value(), b.value())
          case a: DoubleValue => doubleWithDoubleFun(a.value(), b.value())
          case a: LongValue => longWithDoubleFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b => handleUnknown(b)
    }

  private def handleUnknown(a: Any): Nothing =
    throw new IllegalArgumentException(s"unknow primitive: $a")
}

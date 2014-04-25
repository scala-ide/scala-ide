/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy

import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue

/**
 * Provides general way of handling bitwise shifts for primitive proxies.
 * Already handles all cases when bitwise operations are not permitted.
 * But in real case these methods won't be even called due to check on a higher level.
 */
abstract class BitwiseShiftHelper(protected val context: JdiContext) {

  protected def byteWithByteFun(a: Byte, b: Byte): IntJdiProxy
  protected def shortWithByteFun(a: Short, b: Byte): IntJdiProxy
  protected def charWithByteFun(a: Char, b: Byte): IntJdiProxy
  protected def intWithByteFun(a: Int, b: Byte): IntJdiProxy
  protected def longWithByteFun(a: Long, b: Byte): LongJdiProxy

  protected def byteWithShortFun(a: Byte, b: Short): IntJdiProxy
  protected def shortWithShortFun(a: Short, b: Short): IntJdiProxy
  protected def charWithShortFun(a: Char, b: Short): IntJdiProxy
  protected def intWithShortFun(a: Int, b: Short): IntJdiProxy
  protected def longWithShortFun(a: Long, b: Short): LongJdiProxy

  protected def byteWithCharFun(a: Byte, b: Char): IntJdiProxy
  protected def shortWithCharFun(a: Short, b: Char): IntJdiProxy
  protected def charWithCharFun(a: Char, b: Char): IntJdiProxy
  protected def intWithCharFun(a: Int, b: Char): IntJdiProxy
  protected def longWithCharFun(a: Long, b: Char): LongJdiProxy

  protected def byteWithIntFun(a: Byte, b: Int): IntJdiProxy
  protected def shortWithIntFun(a: Short, b: Int): IntJdiProxy
  protected def charWithIntFun(a: Char, b: Int): IntJdiProxy
  protected def intWithIntFun(a: Int, b: Int): IntJdiProxy
  protected def longWithIntFun(a: Long, b: Int): LongJdiProxy

  protected def byteWithLongFun(a: Byte, b: Long): IntJdiProxy
  protected def shortWithLongFun(a: Short, b: Long): IntJdiProxy
  protected def charWithLongFun(a: Char, b: Long): IntJdiProxy
  protected def intWithLongFun(a: Int, b: Long): IntJdiProxy
  protected def longWithLongFun(a: Long, b: Long): LongJdiProxy

  def applyOperation(self: IntegerNumberJdiProxy[_, _], other: IntegerNumberJdiProxy[_, _]): IntegerNumberJdiProxy[_, _] =
    other.primitive match {
      case b: ByteValue =>
        self.primitive match {
          case a: ByteValue => byteWithByteFun(a.value(), b.value())
          case a: ShortValue => shortWithByteFun(a.value(), b.value())
          case a: CharValue => charWithByteFun(a.value(), b.value())
          case a: IntegerValue => intWithByteFun(a.value(), b.value())
          case a: LongValue => longWithByteFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: ShortValue =>
        self.primitive match {
          case a: ByteValue => byteWithShortFun(a.value(), b.value())
          case a: ShortValue => shortWithShortFun(a.value(), b.value())
          case a: CharValue => charWithShortFun(a.value(), b.value())
          case a: IntegerValue => intWithShortFun(a.value(), b.value())
          case a: LongValue => longWithShortFun(a.value(), b.value())
          case a => handleUnknown(a)
        }
      case b: CharValue =>
        self.primitive match {
          case a: ByteValue => byteWithCharFun(a.value(), b.value())
          case a: ShortValue => shortWithCharFun(a.value(), b.value())
          case a: CharValue => charWithCharFun(a.value(), b.value())
          case a: IntegerValue => intWithCharFun(a.value(), b.value())
          case a: LongValue => longWithCharFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: IntegerValue =>
        self.primitive match {
          case a: ByteValue => byteWithIntFun(a.value(), b.value())
          case a: ShortValue => shortWithIntFun(a.value(), b.value())
          case a: CharValue => charWithIntFun(a.value(), b.value())
          case a: IntegerValue => intWithIntFun(a.value(), b.value())
          case a: LongValue => longWithIntFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b: LongValue =>
        self.primitive match {
          case a: ByteValue => byteWithLongFun(a.value(), b.value())
          case a: ShortValue => shortWithLongFun(a.value(), b.value())
          case a: CharValue => charWithLongFun(a.value(), b.value())
          case a: IntegerValue => intWithLongFun(a.value(), b.value())
          case a: LongValue => longWithLongFun(a.value(), b.value())
          case a => handleUnknown(a)
        }

      case b => handleUnknown(b)
    }

  private def handleUnknown(a: Any): Nothing =
    throw new IllegalArgumentException(s"unknow primitive: $a")
}

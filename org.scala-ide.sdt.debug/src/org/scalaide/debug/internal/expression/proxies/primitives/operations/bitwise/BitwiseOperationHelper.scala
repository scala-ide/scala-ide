/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy

import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ShortValue

/**
 * Provides general way of handling bitwise operations for primitive proxies.
 * Already handles all cases when bitwise operations are not permitted.
 * But in real case these methods won't be even called due to check on a higher level.
 */
abstract class BitwiseOperationHelper(protected val context: JdiContext) {

  protected def byteWithByteFun(a: Byte, b: Byte): IntegerNumberJdiProxy[_, _]
  protected def shortWithByteFun(a: Short, b: Byte): IntegerNumberJdiProxy[_, _]
  protected def charWithByteFun(a: Char, b: Byte): IntegerNumberJdiProxy[_, _]
  protected def intWithByteFun(a: Int, b: Byte): IntegerNumberJdiProxy[_, _]
  protected def longWithByteFun(a: Long, b: Byte): IntegerNumberJdiProxy[_, _]

  protected def byteWithShortFun(a: Byte, b: Short): IntegerNumberJdiProxy[_, _]
  protected def shortWithShortFun(a: Short, b: Short): IntegerNumberJdiProxy[_, _]
  protected def charWithShortFun(a: Char, b: Short): IntegerNumberJdiProxy[_, _]
  protected def intWithShortFun(a: Int, b: Short): IntegerNumberJdiProxy[_, _]
  protected def longWithShortFun(a: Long, b: Short): IntegerNumberJdiProxy[_, _]

  protected def byteWithCharFun(a: Byte, b: Char): IntegerNumberJdiProxy[_, _]
  protected def shortWithCharFun(a: Short, b: Char): IntegerNumberJdiProxy[_, _]
  protected def charWithCharFun(a: Char, b: Char): IntegerNumberJdiProxy[_, _]
  protected def intWithCharFun(a: Int, b: Char): IntegerNumberJdiProxy[_, _]
  protected def longWithCharFun(a: Long, b: Char): IntegerNumberJdiProxy[_, _]

  protected def byteWithIntFun(a: Byte, b: Int): IntegerNumberJdiProxy[_, _]
  protected def shortWithIntFun(a: Short, b: Int): IntegerNumberJdiProxy[_, _]
  protected def charWithIntFun(a: Char, b: Int): IntegerNumberJdiProxy[_, _]
  protected def intWithIntFun(a: Int, b: Int): IntegerNumberJdiProxy[_, _]
  protected def longWithIntFun(a: Long, b: Int): IntegerNumberJdiProxy[_, _]

  protected def byteWithLongFun(a: Byte, b: Long): IntegerNumberJdiProxy[_, _]
  protected def shortWithLongFun(a: Short, b: Long): IntegerNumberJdiProxy[_, _]
  protected def charWithLongFun(a: Char, b: Long): IntegerNumberJdiProxy[_, _]
  protected def intWithLongFun(a: Int, b: Long): IntegerNumberJdiProxy[_, _]
  protected def longWithLongFun(a: Long, b: Long): IntegerNumberJdiProxy[_, _]

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

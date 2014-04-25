/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy

/**
 * Implements bitwise >>> operator for primitive proxies.
 * Value is bit-shifted right by the specified number of bits, filling the new left bits with zeros.
 */
trait BitwiseShiftRightWithZeros {
  self: IntegerNumberJdiProxy[_, _] =>

  def >>>(other: IntegerNumberJdiProxy[_, _]): IntegerNumberJdiProxy[_, _] =
    BitwiseShiftRightWithZeros.operationHelper(context).applyOperation(self, other)
}

object BitwiseShiftRightWithZeros {
  protected def operationHelper(ctx: JdiContext) = new BitwiseOperationHelper(ctx: JdiContext) {

    override protected def byteWithByteFun(a: Byte, b: Byte) = context.proxy(a >>> b)
    override protected def shortWithByteFun(a: Short, b: Byte) = context.proxy(a >>> b)
    override protected def charWithByteFun(a: Char, b: Byte) = context.proxy(a >>> b)
    override protected def intWithByteFun(a: Int, b: Byte) = context.proxy(a >>> b)
    override protected def longWithByteFun(a: Long, b: Byte) = context.proxy(a >>> b)

    override protected def byteWithShortFun(a: Byte, b: Short) = context.proxy(a >>> b)
    override protected def shortWithShortFun(a: Short, b: Short) = context.proxy(a >>> b)
    override protected def charWithShortFun(a: Char, b: Short) = context.proxy(a >>> b)
    override protected def intWithShortFun(a: Int, b: Short) = context.proxy(a >>> b)
    override protected def longWithShortFun(a: Long, b: Short) = context.proxy(a >>> b)

    override protected def byteWithCharFun(a: Byte, b: Char) = context.proxy(a >>> b)
    override protected def shortWithCharFun(a: Short, b: Char) = context.proxy(a >>> b)
    override protected def charWithCharFun(a: Char, b: Char) = context.proxy(a >>> b)
    override protected def intWithCharFun(a: Int, b: Char) = context.proxy(a >>> b)
    override protected def longWithCharFun(a: Long, b: Char) = context.proxy(a >>> b)

    override protected def byteWithIntFun(a: Byte, b: Int) = context.proxy(a >>> b)
    override protected def shortWithIntFun(a: Short, b: Int) = context.proxy(a >>> b)
    override protected def charWithIntFun(a: Char, b: Int) = context.proxy(a >>> b)
    override protected def intWithIntFun(a: Int, b: Int) = context.proxy(a >>> b)
    override protected def longWithIntFun(a: Long, b: Int) = context.proxy(a >>> b)

    override protected def byteWithLongFun(a: Byte, b: Long) = context.proxy(a >>> b)
    override protected def shortWithLongFun(a: Short, b: Long) = context.proxy(a >>> b)
    override protected def charWithLongFun(a: Char, b: Long) = context.proxy(a >>> b)
    override protected def intWithLongFun(a: Int, b: Long) = context.proxy(a >>> b)
    override protected def longWithLongFun(a: Long, b: Long) = context.proxy(a >>> b)
  }
}

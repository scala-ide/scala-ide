/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy

/**
 * Implements integer multiplication for primitive proxies.
 */
trait IntegerMultiplication[Proxy <: IntegerNumberJdiProxy[_, Proxy]] {
  self: Proxy =>

  def *[OtherProxy <: NumberJdiProxy[_, OtherProxy], Res <: NumberJdiProxy[_, Res]](other: OtherProxy)(implicit res: NumOpRes[Proxy, OtherProxy, Res]): Res =
    Multiplication.operationHelper(proxyContext).applyIntegerOperation(self, other).asInstanceOf[Res]
}

/**
 * Implements floating point multiplication for primitive proxies.
 */
trait FloatingPointMultiplication[Proxy <: NumberJdiProxy[_, Proxy]] {
  self: Proxy =>

  def *[OtherProxy <: NumberJdiProxy[_, OtherProxy], Res <: NumberJdiProxy[_, Res]](other: OtherProxy)(implicit res: NumOpRes[Proxy, OtherProxy, Res]): Res =
    Multiplication.operationHelper(proxyContext).applyFloatingPointOperation(self, other).asInstanceOf[Res]
}

object Multiplication {
  private[operations] def operationHelper(ctx: JdiContext) = new NumericOperationHelper(ctx) {
    override protected def byteWithByteFun(a: Byte, b: Byte) = context.proxy(a * b)
    override protected def shortWithByteFun(a: Short, b: Byte) = context.proxy(a * b)
    override protected def charWithByteFun(a: Char, b: Byte) = context.proxy(a * b)
    override protected def intWithByteFun(a: Int, b: Byte) = context.proxy(a * b)
    override protected def floatWithByteFun(a: Float, b: Byte) = context.proxy(a * b)
    override protected def doubleWithByteFun(a: Double, b: Byte) = context.proxy(a * b)
    override protected def longWithByteFun(a: Long, b: Byte) = context.proxy(a * b)

    override protected def byteWithShortFun(a: Byte, b: Short) = context.proxy(a * b)
    override protected def shortWithShortFun(a: Short, b: Short) = context.proxy(a * b)
    override protected def charWithShortFun(a: Char, b: Short) = context.proxy(a * b)
    override protected def intWithShortFun(a: Int, b: Short) = context.proxy(a * b)
    override protected def floatWithShortFun(a: Float, b: Short) = context.proxy(a * b)
    override protected def doubleWithShortFun(a: Double, b: Short) = context.proxy(a * b)
    override protected def longWithShortFun(a: Long, b: Short) = context.proxy(a * b)

    override protected def byteWithCharFun(a: Byte, b: Char) = context.proxy(a * b)
    override protected def shortWithCharFun(a: Short, b: Char) = context.proxy(a * b)
    override protected def charWithCharFun(a: Char, b: Char) = context.proxy(a * b)
    override protected def intWithCharFun(a: Int, b: Char) = context.proxy(a * b)
    override protected def floatWithCharFun(a: Float, b: Char) = context.proxy(a * b)
    override protected def doubleWithCharFun(a: Double, b: Char) = context.proxy(a * b)
    override protected def longWithCharFun(a: Long, b: Char) = context.proxy(a * b)

    override protected def byteWithIntFun(a: Byte, b: Int) = context.proxy(a * b)
    override protected def shortWithIntFun(a: Short, b: Int) = context.proxy(a * b)
    override protected def charWithIntFun(a: Char, b: Int) = context.proxy(a * b)
    override protected def intWithIntFun(a: Int, b: Int) = context.proxy(a * b)
    override protected def floatWithIntFun(a: Float, b: Int) = context.proxy(a * b)
    override protected def doubleWithIntFun(a: Double, b: Int) = context.proxy(a * b)
    override protected def longWithIntFun(a: Long, b: Int) = context.proxy(a * b)

    override protected def byteWithLongFun(a: Byte, b: Long) = context.proxy(a * b)
    override protected def shortWithLongFun(a: Short, b: Long) = context.proxy(a * b)
    override protected def charWithLongFun(a: Char, b: Long) = context.proxy(a * b)
    override protected def intWithLongFun(a: Int, b: Long) = context.proxy(a * b)
    override protected def floatWithLongFun(a: Float, b: Long) = context.proxy(a * b)
    override protected def doubleWithLongFun(a: Double, b: Long) = context.proxy(a * b)
    override protected def longWithLongFun(a: Long, b: Long) = context.proxy(a * b)

    override protected def byteWithFloatFun(a: Byte, b: Float) = context.proxy(a * b)
    override protected def shortWithFloatFun(a: Short, b: Float) = context.proxy(a * b)
    override protected def charWithFloatFun(a: Char, b: Float) = context.proxy(a * b)
    override protected def intWithFloatFun(a: Int, b: Float) = context.proxy(a * b)
    override protected def floatWithFloatFun(a: Float, b: Float) = context.proxy(a * b)
    override protected def doubleWithFloatFun(a: Double, b: Float) = context.proxy(a * b)
    override protected def longWithFloatFun(a: Long, b: Float) = context.proxy(a * b)

    override protected def byteWithDoubleFun(a: Byte, b: Double) = context.proxy(a * b)
    override protected def shortWithDoubleFun(a: Short, b: Double) = context.proxy(a * b)
    override protected def charWithDoubleFun(a: Char, b: Double) = context.proxy(a * b)
    override protected def intWithDoubleFun(a: Int, b: Double) = context.proxy(a * b)
    override protected def floatWithDoubleFun(a: Float, b: Double) = context.proxy(a * b)
    override protected def doubleWithDoubleFun(a: Double, b: Double) = context.proxy(a * b)
    override protected def longWithDoubleFun(a: Long, b: Double) = context.proxy(a * b)
  }
}

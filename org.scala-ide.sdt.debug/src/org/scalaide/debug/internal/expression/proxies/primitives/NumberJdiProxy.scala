/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.ScalaNumberProxy

import org.scalaide.debug.internal.expression.proxies.primitives.operations.BitwiseOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.FloatingPointNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.IntegerNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumberConversion
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumericComparison
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryBitwiseNegation
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryMinus
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryPlus

/**
 * Base for all numeric boxed primitives proxies.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class NumberJdiProxy[Primitive: Numeric, Proxy <: NumberJdiProxy[Primitive, Proxy]](
  companion: BoxedJdiProxyCompanion[Primitive, Proxy])(
    implicit val num: Numeric[Primitive])
  extends BoxedJdiProxy[Primitive, Proxy](companion)
  with NumberConversion[Primitive]
  with NumericComparison { self: Proxy =>

  protected def numberProxy: ScalaNumberProxy[Primitive]

  def min(that: NumberJdiProxy[Primitive, Proxy]): Proxy = companion.fromPrimitive(numberProxy.min(that.primitiveValue), context)
  def max(that: NumberJdiProxy[Primitive, Proxy]): Proxy = companion.fromPrimitive(numberProxy.max(that.primitiveValue), context)
  def abs: Proxy = companion.fromPrimitive(numberProxy.abs, context)
  def signum: IntJdiProxy = context.proxy(numberProxy.signum)

  def isValidByte = context.proxy(numberProxy.isValidByte)
  def isValidShort = context.proxy(numberProxy.isValidShort)
  def isValidInt = context.proxy(numberProxy.isValidInt)
  def isValidChar = context.proxy(numberProxy.isValidChar)

  def isWhole() = context.proxy(numberProxy.isWhole())
}

/**
 * Base for all numeric boxed primitives based on integer arithmetics.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class IntegerNumberJdiProxy[Primitive: Integral, Proxy <: IntegerNumberJdiProxy[Primitive, Proxy]](
  companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends NumberJdiProxy[Primitive, Proxy](companion)
  with IntegerNumericOperations
  with FloatingPointNumericOperations
  with BitwiseOperations
  with UnaryMinus[IntegerNumberJdiProxy[_, _]]
  with UnaryPlus[IntegerNumberJdiProxy[_, _]]
  with UnaryBitwiseNegation { self: Proxy =>
}

/**
 * Base for all numeric boxed primitives based on floating point arithmetic.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class FloatingPointNumberJdiProxy[Primitive: Fractional, Proxy <: FloatingPointNumberJdiProxy[Primitive, Proxy]](
  companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends NumberJdiProxy[Primitive, Proxy](companion)
  with FloatingPointNumericOperations
  with UnaryMinus[FloatingPointNumberJdiProxy[_, _]]
  with UnaryPlus[FloatingPointNumberJdiProxy[_, _]] { self: Proxy =>

  protected implicit def integralNum: Integral[Primitive]
}

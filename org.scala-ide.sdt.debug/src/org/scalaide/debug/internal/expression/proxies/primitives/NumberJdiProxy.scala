/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.proxies.primitives.operations.BitwiseOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.FloatingPointNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.IntegerNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumberConversion
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumericComparison

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
  with IntegerNumericOperations[Primitive, Proxy]
  with BitwiseOperations[Primitive, Proxy] { self: Proxy => }

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
  with FloatingPointNumericOperations[Primitive, Proxy] { self: Proxy => }

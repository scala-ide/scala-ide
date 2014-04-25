/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.proxies.primitives.operations.BitwiseOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.FloatingPointNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.IntegerNumericOperations
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumberConversion
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryMinus
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryPlus
import org.scalaide.debug.internal.expression.proxies.primitives.operations.UnaryBitwiseNegation
import org.scalaide.debug.internal.expression.proxies.primitives.operations.NumericComparison

/**
 * Base for all numeric boxed primitives proxies.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class NumberJdiProxy[Primitive: Numeric, Proxy <: NumberJdiProxy[Primitive, Proxy]](companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends BoxedJdiProxy[Primitive, Proxy](companion)
  with NumberConversion[Primitive]
  with NumericComparison { self: Proxy =>

  protected def num: Numeric[Primitive] = implicitly[Numeric[Primitive]]
}

/**
 * Base for all numeric boxed primitives based on integer arithmetics.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class IntegerNumberJdiProxy[Primitive: Numeric, Proxy <: IntegerNumberJdiProxy[Primitive, Proxy]](companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends NumberJdiProxy[Primitive, Proxy](companion)
  with IntegerNumericOperations
  with FloatingPointNumericOperations
  with BitwiseOperations
  with UnaryMinus[IntegerNumberJdiProxy[_, _]]
  with UnaryPlus[IntegerNumberJdiProxy[_, _]]
  with UnaryBitwiseNegation { self: Proxy => }

/**
 * Base for all numeric boxed primitives based on floating point arithmetic.
 *
 * @tparam Primitive type to proxy, must have a ``scala.math.Numeric``
 * @tparam Proxy type of proxy, must be subtype of ``NumberJdiProxy``
 * @param companion companion object for this proxy
 */
abstract class FloatingPointNumberJdiProxy[Primitive: Numeric, Proxy <: FloatingPointNumberJdiProxy[Primitive, Proxy]](companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends NumberJdiProxy[Primitive, Proxy](companion)
  with FloatingPointNumericOperations
  with UnaryMinus[FloatingPointNumberJdiProxy[_, _]]
  with UnaryPlus[FloatingPointNumberJdiProxy[_, _]] { self: Proxy => }

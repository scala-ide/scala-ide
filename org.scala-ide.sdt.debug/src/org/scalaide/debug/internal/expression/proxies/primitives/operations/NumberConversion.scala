/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

trait NumberConversion[Primitive] { self: BoxedJdiProxy[Primitive, _] =>

  def toByte = proxyContext.proxy(num.toInt(primitiveValue).toByte)
  def toShort = proxyContext.proxy(num.toInt(primitiveValue).toShort)
  def toChar = proxyContext.proxy(num.toInt(primitiveValue).toChar)
  def toDouble = proxyContext.proxy(num.toDouble(primitiveValue))
  def toFloat = proxyContext.proxy(num.toFloat(primitiveValue))
  def toInt = proxyContext.proxy(num.toInt(primitiveValue))
  def toLong = proxyContext.proxy(num.toLong(primitiveValue))

  protected def num: Numeric[Primitive]
  protected def primitiveValue: Primitive
}

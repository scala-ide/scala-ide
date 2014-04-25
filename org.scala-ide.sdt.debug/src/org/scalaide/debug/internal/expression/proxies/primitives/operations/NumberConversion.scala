/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy

trait NumberConversion[Primitive] { self: BoxedJdiProxy[Primitive, _] =>

  def toByte = context.proxy(num.toInt(primitiveValue).toByte)
  def toShort = context.proxy(num.toInt(primitiveValue).toShort)
  def toChar = context.proxy(num.toInt(primitiveValue).toChar)
  def toDouble = context.proxy(num.toDouble(primitiveValue))
  def toFloat = context.proxy(num.toFloat(primitiveValue))
  def toInt = context.proxy(num.toInt(primitiveValue))
  def toLong = context.proxy(num.toLong(primitiveValue))

  protected def num: Numeric[Primitive]
  protected def primitiveValue: Primitive
}

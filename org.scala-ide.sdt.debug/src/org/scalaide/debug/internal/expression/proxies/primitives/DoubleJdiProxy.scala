/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.RichDouble

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.DoubleValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `double`, `scala.Double` and `java.lang.Double`.
 */
case class DoubleJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends FloatingPointNumberJdiProxy[Double, DoubleJdiProxy](DoubleJdiProxy) {

  def unary_- = context.proxy(-primitiveValue)

  override def primitiveValue = this.primitive.asInstanceOf[DoubleValue].value()
  protected override def numberProxy = new RichDouble(primitiveValue)
  protected override def integralNum = scala.math.Numeric.DoubleAsIfIntegral

  def round: LongJdiProxy = context.proxy(math.round(primitiveValue))
  def ceil: DoubleJdiProxy = context.proxy(math.ceil(primitiveValue))
  def floor: DoubleJdiProxy = context.proxy(math.floor(primitiveValue))

  def toRadians: DoubleJdiProxy = context.proxy(math.toRadians(primitiveValue))
  def toDegrees: DoubleJdiProxy = context.proxy(math.toDegrees(primitiveValue))

  def isInfinity: BooleanJdiProxy = context.proxy(java.lang.Double.isInfinite(primitiveValue))
  def isPosInfinity: BooleanJdiProxy = isInfinity && context.proxy(primitiveValue > 0.0)
  def isNegInfinity: BooleanJdiProxy = isInfinity && context.proxy(primitiveValue < 0.0)
}

object DoubleJdiProxy extends BoxedJdiProxyCompanion[Double, DoubleJdiProxy](JavaBoxed.Double, JavaPrimitives.double) {
  protected def mirror(value: Double, context: JdiContext): Value = context.mirrorOf(value)
}
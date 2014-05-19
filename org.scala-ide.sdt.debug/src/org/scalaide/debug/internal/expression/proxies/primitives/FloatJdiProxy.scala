/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.RichFloat

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.FloatValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `float`, `scala.Float`, `java.lang.Float`.
 */
case class FloatJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends FloatingPointNumberJdiProxy[Float, FloatJdiProxy](FloatJdiProxy) {

  def unary_- = context.proxy(-primitiveValue)

  protected def primitiveValue = this.primitive.asInstanceOf[FloatValue].value()
  protected override def numberProxy = new RichFloat(primitiveValue)
  protected override def integralNum = scala.math.Numeric.FloatAsIfIntegral

  def round: IntJdiProxy = context.proxy(math.round(primitiveValue))
  def ceil: FloatJdiProxy = context.proxy(math.ceil(primitiveValue).toFloat)
  def floor: FloatJdiProxy = context.proxy(math.floor(primitiveValue).toFloat)

  def toRadians: FloatJdiProxy = context.proxy(math.toRadians(primitiveValue).toFloat)
  def toDegrees: FloatJdiProxy = context.proxy(math.toDegrees(primitiveValue).toFloat)

  def isInfinity: BooleanJdiProxy = context.proxy(java.lang.Float.isInfinite(primitiveValue))
  def isPosInfinity: BooleanJdiProxy = isInfinity && context.proxy(primitiveValue > 0.0f)
  def isNegInfinity: BooleanJdiProxy = isInfinity && context.proxy(primitiveValue < 0.0f)
}

object FloatJdiProxy extends BoxedJdiProxyCompanion[Float, FloatJdiProxy](JavaBoxed.Float, JavaPrimitives.float) {
  protected def mirror(value: Float, context: JdiContext): Value = context.mirrorOf(value)
}
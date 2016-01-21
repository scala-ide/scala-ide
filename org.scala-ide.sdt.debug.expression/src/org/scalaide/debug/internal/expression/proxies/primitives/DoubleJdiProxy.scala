/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.DoubleValue

/**
 * JdiProxy implementation for `double`, `scala.Double` and `java.lang.Double`.
 */
case class DoubleJdiProxy(__context: JdiContext, __value: DoubleValue)
    extends PrimitiveJdiProxy[Double, DoubleJdiProxy, DoubleValue](DoubleJdiProxy) {

  override def __primitiveValue[I]: I = this.__value.value.asInstanceOf[I]
}

object DoubleJdiProxy extends PrimitiveJdiProxyCompanion[Double, DoubleJdiProxy, DoubleValue](TypeNames.Double) {
  protected def mirror(value: Double, context: JdiContext): DoubleValue = context.mirrorOf(value)
}

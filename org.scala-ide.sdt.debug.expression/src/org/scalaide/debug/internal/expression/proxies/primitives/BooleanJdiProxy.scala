/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.BooleanValue

/**
 * JdiProxy implementation for `bool`, `scala.Boolean` and `java.lang.Boolean`.
 */
case class BooleanJdiProxy(__context: JdiContext, __value: BooleanValue)
    extends PrimitiveJdiProxy[Boolean, BooleanJdiProxy, BooleanValue](BooleanJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object BooleanJdiProxy extends PrimitiveJdiProxyCompanion[Boolean, BooleanJdiProxy, BooleanValue](TypeNames.Boolean) {
  protected def mirror(value: Boolean, context: JdiContext): BooleanValue = context.mirrorOf(value)
}

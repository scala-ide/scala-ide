/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ShortValue

/**
 * JdiProxy implementation for `short` and `scala.Short` and `java.lang.Short`.
 */
case class ShortJdiProxy(__context: JdiContext, __value: ShortValue)
    extends PrimitiveJdiProxy[Short, ShortJdiProxy, ShortValue](ShortJdiProxy) {

  override def __primitiveValue[I] = this.__value.value().asInstanceOf[I]
}

object ShortJdiProxy extends PrimitiveJdiProxyCompanion[Short, ShortJdiProxy, ShortValue](TypeNames.Short) {
  protected def mirror(value: Short, context: JdiContext): ShortValue = context.mirrorOf(value)
}

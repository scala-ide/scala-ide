/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.LongValue

/**
 * JdiProxy implementation for `long`, `java.lang.Long` and `scala.Long`.
 */
case class LongJdiProxy(__context: JdiContext, __value: LongValue)
    extends PrimitiveJdiProxy[Long, LongJdiProxy, LongValue](LongJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object LongJdiProxy extends PrimitiveJdiProxyCompanion[Long, LongJdiProxy, LongValue](TypeNames.Long) {
  protected def mirror(value: Long, context: JdiContext): LongValue = context.mirrorOf(value)
}

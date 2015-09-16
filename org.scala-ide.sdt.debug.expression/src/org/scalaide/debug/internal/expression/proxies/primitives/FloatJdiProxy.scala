/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.FloatValue

/**
 * JdiProxy implementation for `float`, `scala.Float`, `java.lang.Float`.
 */
case class FloatJdiProxy(__context: JdiContext, __value: FloatValue)
    extends PrimitiveJdiProxy[Float, FloatJdiProxy, FloatValue](FloatJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object FloatJdiProxy extends PrimitiveJdiProxyCompanion[Float, FloatJdiProxy, FloatValue](TypeNames.Float) {
  protected def mirror(value: Float, context: JdiContext): FloatValue = context.mirrorOf(value)
}

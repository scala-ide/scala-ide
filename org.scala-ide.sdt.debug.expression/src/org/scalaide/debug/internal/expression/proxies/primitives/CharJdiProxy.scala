/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.CharValue

/**
 * JdiProxy implementation for `char` and `scala.Char` and `java.lang.Character`.
 */
case class CharJdiProxy(__context: JdiContext, __value: CharValue)
  extends PrimitiveJdiProxy[Char, CharJdiProxy, CharValue](CharJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object CharJdiProxy extends PrimitiveJdiProxyCompanion[Char, CharJdiProxy, CharValue](TypeNames.Char) {
  protected def mirror(value: Char, context: JdiContext): CharValue = context.mirrorOf(value)
}

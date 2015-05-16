/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.IntegerValue

/**
 * JdiProxy implementation for `int`, `scala.Int` and `java.lang.Integer`.
 */
case class IntJdiProxy(__context: JdiContext, __value: IntegerValue)
    extends PrimitiveJdiProxy[Int, IntJdiProxy, IntegerValue](IntJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object IntJdiProxy extends PrimitiveJdiProxyCompanion[Int, IntJdiProxy, IntegerValue](TypeNames.Int) {
  protected def mirror(value: Int, context: JdiContext): IntegerValue = context.mirrorOf(value)
}

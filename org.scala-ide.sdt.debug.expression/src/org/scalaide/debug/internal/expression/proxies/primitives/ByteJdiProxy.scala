/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ByteValue

/**
 * JdiProxy implementation for `byte` and `scala.Byte` and `java.lang.Byte`.
 */
case class ByteJdiProxy(__context: JdiContext, __value: ByteValue)
    extends PrimitiveJdiProxy[Byte, ByteJdiProxy, ByteValue](ByteJdiProxy) {

  override def __primitiveValue[I] = __value.value.asInstanceOf[I]
}

object ByteJdiProxy extends PrimitiveJdiProxyCompanion[Byte, ByteJdiProxy, ByteValue](TypeNames.Byte) {
  protected def mirror(value: Byte, context: JdiContext): ByteValue = context.mirrorOf(value)
}

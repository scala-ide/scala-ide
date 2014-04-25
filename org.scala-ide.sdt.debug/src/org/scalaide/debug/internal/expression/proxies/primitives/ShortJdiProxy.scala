/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ObjectReference
import com.sun.jdi.ShortValue
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `short` and `scala.Short` and `java.lang.Short`.
 */
case class ShortJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Short, ShortJdiProxy](ShortJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichShort")

  def unary_- : IntJdiProxy = proxyContext.proxy(-primitiveValue)
  def unary_+ : IntJdiProxy = proxyContext.proxy(+primitiveValue)

  def unary_~ : IntJdiProxy = proxyContext.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[ShortValue].value()
}

object ShortJdiProxy extends BoxedJdiProxyCompanion[Short, ShortJdiProxy](Java.boxed.Short, Java.primitives.short) {
  protected def mirror(value: Short, context: JdiContext): Value = context.mirrorOf(value)
}
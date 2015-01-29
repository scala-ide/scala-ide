/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.BooleanValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `bool`, `scala.Boolean` and `java.lang.Boolean`.
 */
case class BooleanJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Boolean, BooleanJdiProxy](BooleanJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichBoolean")

  override def __value[I] = primitive.asInstanceOf[BooleanValue].value().asInstanceOf[I]

}

object BooleanJdiProxy extends BoxedJdiProxyCompanion[Boolean, BooleanJdiProxy](Java.boxed.Boolean, Java.primitives.boolean) {
  protected def mirror(value: Boolean, context: JdiContext): Value = context.mirrorOf(value)
}
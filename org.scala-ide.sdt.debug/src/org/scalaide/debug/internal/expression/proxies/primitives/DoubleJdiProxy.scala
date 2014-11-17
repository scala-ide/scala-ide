/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.DoubleValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `double`, `scala.Double` and `java.lang.Double`.
 */
case class DoubleJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Double, DoubleJdiProxy](DoubleJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichDouble")

  override def _DoubleMirror: Double = this.primitive.asInstanceOf[DoubleValue].value()
}

object DoubleJdiProxy extends BoxedJdiProxyCompanion[Double, DoubleJdiProxy](Java.boxed.Double, Java.primitives.double) {
  protected def mirror(value: Double, context: JdiContext): Value = context.mirrorOf(value)
}
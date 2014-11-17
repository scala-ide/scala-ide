/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.FloatValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `float`, `scala.Float`, `java.lang.Float`.
 */
case class FloatJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Float, FloatJdiProxy](FloatJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichFloat")

  override def _FloatMirror: Float = this.primitive.asInstanceOf[FloatValue].value()
}

object FloatJdiProxy extends BoxedJdiProxyCompanion[Float, FloatJdiProxy](Java.boxed.Float, Java.primitives.float) {
  protected def mirror(value: Float, context: JdiContext): Value = context.mirrorOf(value)
}
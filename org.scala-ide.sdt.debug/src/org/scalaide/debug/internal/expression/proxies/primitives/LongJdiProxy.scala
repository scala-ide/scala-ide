/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `long`, `java.lang.Long` and `scala.Long`.
 */
case class LongJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Long, LongJdiProxy](LongJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichLong")

  override def _LongMirror: Long = this.primitive.asInstanceOf[LongValue].value()
}

object LongJdiProxy extends BoxedJdiProxyCompanion[Long, LongJdiProxy](Java.boxed.Long, Java.primitives.long) {
  protected def mirror(value: Long, context: JdiContext): Value = context.mirrorOf(value)
}
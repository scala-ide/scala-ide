/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `int`, `scala.Int` and `java.lang.Integer`.
 */
case class IntJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Int, IntJdiProxy](IntJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichInt")

  override def __value[I] = primitive.asInstanceOf[IntegerValue].value().asInstanceOf[I]
}

object IntJdiProxy extends BoxedJdiProxyCompanion[Int, IntJdiProxy](Java.boxed.Integer, Java.primitives.int) {
  protected def mirror(value: Int, context: JdiContext): Value = context.mirrorOf(value)
}
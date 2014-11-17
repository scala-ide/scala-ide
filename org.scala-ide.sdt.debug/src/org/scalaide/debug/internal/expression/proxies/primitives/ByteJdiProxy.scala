/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ByteValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.scalaide.debug.internal.expression.proxies.JdiProxy

/**
 * JdiProxy implementation for `byte` and `scala.Byte` and `java.lang.Byte`.
 */
case class ByteJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends BoxedJdiProxy[Byte, ByteJdiProxy](ByteJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichByte")

  override def _ByteMirror: Byte = this.primitive.asInstanceOf[ByteValue].value()
}

object ByteJdiProxy extends BoxedJdiProxyCompanion[Byte, ByteJdiProxy](Java.boxed.Byte, Java.primitives.byte) {
  protected def mirror(value: Byte, context: JdiContext): Value = context.mirrorOf(value)
}
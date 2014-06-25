/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ByteValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `byte` and `scala.Byte` and `java.lang.Byte`.
 */
case class ByteJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Byte, ByteJdiProxy](ByteJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichByte")

  override def unary_- : IntJdiProxy = context.proxy(-primitiveValue)
  override def unary_~ : IntJdiProxy = context.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[ByteValue].value()
}

object ByteJdiProxy extends BoxedJdiProxyCompanion[Byte, ByteJdiProxy](JavaBoxed.Byte, JavaPrimitives.byte) {
  protected def mirror(value: Byte, context: JdiContext): Value = context.mirrorOf(value)
}
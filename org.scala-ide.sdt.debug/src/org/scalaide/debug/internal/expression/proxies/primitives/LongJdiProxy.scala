/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `long`, `java.lang.Long` and `scala.Long`.
 */
case class LongJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Long, LongJdiProxy](LongJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.LongChar")

  override def unary_- : LongJdiProxy = context.proxy(-primitiveValue)
  override def unary_~ : LongJdiProxy = context.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[LongValue].value()

}

object LongJdiProxy extends BoxedJdiProxyCompanion[Long, LongJdiProxy](JavaBoxed.Long, JavaPrimitives.long) {
  protected def mirror(value: Long, context: JdiContext): Value = context.mirrorOf(value)
}
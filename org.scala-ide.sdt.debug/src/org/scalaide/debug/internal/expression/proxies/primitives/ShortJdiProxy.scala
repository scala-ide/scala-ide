/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ObjectReference
import com.sun.jdi.ShortValue
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `short` and `scala.Short` and `java.lang.Short`.
 */
case class ShortJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Short, ShortJdiProxy](ShortJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichShort")

  override def unary_- : IntJdiProxy = context.proxy(-primitiveValue)

  override def unary_~ : IntJdiProxy = context.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[ShortValue].value()
}

object ShortJdiProxy extends BoxedJdiProxyCompanion[Short, ShortJdiProxy](JavaBoxed.Short, JavaPrimitives.short) {
  protected def mirror(value: Short, context: JdiContext): Value = context.mirrorOf(value)
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.RichInt
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

/**
 * JdiProxy implementation for `int`, `scala.Int` and `java.lang.Integer`.
 */
case class IntJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Int, IntJdiProxy](IntJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichInt")

  override def unary_- = context.proxy(-primitiveValue)

  override def unary_~ : IntJdiProxy = context.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[IntegerValue].value()
}

object IntJdiProxy extends BoxedJdiProxyCompanion[Int, IntJdiProxy](JavaBoxed.Integer, JavaPrimitives.int) {
  protected def mirror(value: Int, context: JdiContext): Value = context.mirrorOf(value)
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.FloatValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `float`, `scala.Float`, `java.lang.Float`.
 */
case class FloatJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends FloatingPointNumberJdiProxy[Float, FloatJdiProxy](FloatJdiProxy) {

  def unary_- = context.proxy(-primitiveValue)

  protected def primitiveValue = this.primitive.asInstanceOf[FloatValue].value()
}

object FloatJdiProxy extends BoxedJdiProxyCompanion[Float, FloatJdiProxy](JavaBoxed.Float, JavaPrimitives.float) {
  protected def mirror(value: Float, context: JdiContext): Value = context.mirrorOf(value)
}
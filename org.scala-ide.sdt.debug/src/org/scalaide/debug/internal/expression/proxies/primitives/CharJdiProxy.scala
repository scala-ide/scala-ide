/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.runtime.RichChar

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.CharValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `char` and `scala.Char` and `java.lang.Character`.
 */
case class CharJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Char, CharJdiProxy](CharJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichChar")

  override def unary_- : IntJdiProxy = context.proxy(-primitiveValue)
  override def unary_~ : IntJdiProxy = context.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[CharValue].value()
}

object CharJdiProxy extends BoxedJdiProxyCompanion[Char, CharJdiProxy](JavaBoxed.Character, JavaPrimitives.char) {
  protected def mirror(value: Char, context: JdiContext): Value = context.mirrorOf(value)
}
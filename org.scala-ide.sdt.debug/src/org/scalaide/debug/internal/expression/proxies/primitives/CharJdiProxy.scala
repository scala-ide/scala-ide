/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.CharValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `char` and `scala.Char` and `java.lang.Character`.
 */
case class CharJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference)
  extends IntegerNumberJdiProxy[Char, CharJdiProxy](CharJdiProxy) {

  override protected[expression] def genericThisType: Option[String] = Some("scala.runtime.RichChar")

  def unary_- : IntJdiProxy = proxyContext.proxy(-primitiveValue)
  def unary_+ : IntJdiProxy = proxyContext.proxy(+primitiveValue)

  def unary_~ : IntJdiProxy = proxyContext.proxy(~primitiveValue)

  protected override def primitiveValue = this.primitive.asInstanceOf[CharValue].value()
}

object CharJdiProxy extends BoxedJdiProxyCompanion[Char, CharJdiProxy](Java.boxed.Character, Java.primitives.char) {
  protected def mirror(value: Char, context: JdiContext): Value = context.mirrorOf(value)
}
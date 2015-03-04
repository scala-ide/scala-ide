/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

import com.sun.jdi.Value

/**
 * Custom handler for string concatenation (`obj + String` or `String + obj`).
 *
 * Those calls are replaced with `String.concat(a, b)`.
 */
class StringConcatenationMethod(proxy: JdiProxy, name: String, args: Seq[JdiProxy]) extends MethodInvoker {
  private val context = proxy.proxyContext

  private def stringify(proxy: JdiProxy) = StringJdiProxy(context, context.callToString(proxy))

  private def callConcatMethod(proxy: JdiProxy, arg: JdiProxy) =
    context.tryInvokeUnboxed(proxy, None, "concat", Seq(stringify(arg)))

  override def apply(): Option[Value] = (name, args) match {
    case ("+" | "$plus", Seq(arg)) =>
      (proxy.referenceType.name, arg.referenceType.name) match {
        case (Java.boxed.String, _) => callConcatMethod(proxy, arg)
        case (_, Java.boxed.String) => callConcatMethod(stringify(proxy), arg)
        case _ => None
      }
    case _ => None
  }
}
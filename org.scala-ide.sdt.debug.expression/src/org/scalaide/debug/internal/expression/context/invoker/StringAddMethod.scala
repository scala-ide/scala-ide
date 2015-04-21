/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

import com.sun.jdi.Value

/**
 * Custom handler for string concatenation (`obj + String` or `String + obj`).
 *
 * Those calls are replaced with `String.concat(a, b)`.
 */
class StringConcatenationMethod(proxy: JdiProxy, name: String, args: Seq[JdiProxy], context: JdiContext)
    extends MethodInvoker {

  private def stringify(proxy: JdiProxy) = StringJdiProxy(context, context.callToString(proxy.__autoboxed))

  private def callConcatMethod(proxy: JdiProxy, arg: JdiProxy) =
    context.tryInvokeUnboxed(proxy, None, "concat", Seq(stringify(arg)))

  override def apply(): Option[Value] = (name, args) match {
    case ("+" | "$plus", Seq(arg)) =>
      (proxy.__type.name, arg.__type.name) match {
        case (Java.boxed.String, _) => callConcatMethod(proxy, arg)
        case (_, Java.boxed.String) => callConcatMethod(stringify(proxy), arg)
        case _ => None
      }
    case _ => None
  }
}

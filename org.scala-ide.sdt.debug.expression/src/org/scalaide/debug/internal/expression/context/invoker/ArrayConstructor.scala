/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy

import com.sun.jdi.Value

class ArrayConstructor(className: String, args: Seq[JdiProxy], context: JdiContext) extends MethodInvoker {
  def apply(): Option[Value] = args match {
    case List(proxy: IntJdiProxy) =>
      val arrayType = context.arrayByName(className)
      Some(arrayType.newInstance(proxy.__value[Int]))
    case other => None
  }
}

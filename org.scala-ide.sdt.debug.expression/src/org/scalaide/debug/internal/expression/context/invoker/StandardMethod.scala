/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Calls standard method on given `ObjectReference` in context of debug.
 */
class StandardMethod(proxy: JdiProxy, name: String, val args: Seq[JdiProxy], context: JdiContext)
    extends BaseMethodInvoker {

  override val methodName: String = NameTransformer.encode(name)

  override def referenceType: ReferenceType = proxy.referenceType

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val finalArgs = generateArguments(method)
      proxy.__underlying.invokeMethod(context.currentThread(), method, finalArgs)
    }

    matching match {
      case Nil => None
      case single +: Nil => Some(invoke(single))
      case multiple =>
        logger.warn(multipleOverloadsMessage(multiple))
        Some(invoke(multiple.head))
    }
  }
}

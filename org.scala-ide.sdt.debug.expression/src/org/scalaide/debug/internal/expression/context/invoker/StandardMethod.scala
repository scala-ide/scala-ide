/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Implementation of `BaseMethodInvoker`for Scala methods.
 */
abstract class ScalaMethod(val methodName: String, proxy: JdiProxy) extends BaseMethodInvoker {
  protected def referenceType: ReferenceType = proxy.referenceType
}

/**
 * Calls standard method on given `ObjectReference` in context of debug.
 */
class StandardMethod(proxy: JdiProxy, name: String, val args: Seq[JdiProxy], context: JdiContext)
    extends ScalaMethod(name, proxy) {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value =
      proxy.__underlying.invokeMethod(context.currentThread(), method, generateArguments(method))

    handleMultipleOverloads(matching, invoke)
  }
}

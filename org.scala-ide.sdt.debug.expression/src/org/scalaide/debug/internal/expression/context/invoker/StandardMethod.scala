/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy

import com.sun.jdi.Method
import com.sun.jdi.Value

/**
 * Calls standard method on given `ObjectReference` in context of debug.
 */
class StandardMethod(proxy: ObjectJdiProxy, val methodName: String, val args: Seq[JdiProxy],
    realThisType: Option[String], context: JdiContext)
  extends ScalaMethod(realThisType, proxy) {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value =
      invokeMethod(context.currentThread(), method, generateArguments(method))

    handleMultipleOverloads(matching, invoke)
  }
}

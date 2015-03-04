/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.proxies.JdiProxy
import com.sun.jdi.ClassType
import com.sun.jdi.Value
import org.scalaide.debug.internal.expression.context.JdiContext

/**
 * Calls standard static Java methods in context of debug.
 */
class JavaStaticMethod(val referenceType: ClassType, val methodName: String, val args: Seq[JdiProxy], context: JdiContext)
    extends BaseMethodInvoker {
  override def apply(): Option[Value] =
    for {
      // TODO same as in all other places, only invokes first overload
      // TODO handle vararg
      requestedMethod <- matching.find(_.isStatic())
      finalArgs = generateArguments(requestedMethod)
    } yield referenceType.invokeMethod(context.currentThread(), requestedMethod, finalArgs)
}
/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.context.JdiMethodInvoker
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy

import com.sun.jdi.Value

/**
 * Custom handler for AnyVal calls
 * Call like on.method(restOfParams) is replaced with `CompanionObject.method(on, restOfParams)` or `new BoxingClass(on).method(restOfParams)`
 */
class AnyValMethod(proxy: JdiProxy, methodName: String, args: Seq[JdiProxy], realThisType: Option[String], context: JdiContext, invoker: JdiMethodInvoker)
    extends MethodInvoker {

  private def companionObject = for {
    companionObjectName <- realThisType
    objectReference <- context.tryObjectByName(companionObjectName)
  } yield ObjectJdiProxy(context, objectReference)

  private def invokeDelegate: Option[Value] = for {
    companionObject <- companionObject
    extensionName = methodName + "$extension"
    newArgs = proxy +: args
    value <- context.tryInvokeUnboxed(companionObject, None, extensionName, newArgs)
  } yield value

  private def invokedBoxed: Option[Value] = for {
    className <- realThisType
    boxed <- invoker.tryNewInstance(className, Seq(proxy))
    proxied = context.valueProxy(boxed)
    res <- context.tryInvokeUnboxed(proxied, None, methodName, args)
  } yield res

  /** invoke delegate or box value and invoke method */
  override def apply(): Option[Value] = invokeDelegate orElse invokedBoxed
}

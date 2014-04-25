/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ObjectReference

/**
 * JdiProxy implementation for `java.lang.String`.
 */
case class StringJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference) extends JdiProxy {

  final def +(proxy: JdiProxy): StringJdiProxy =
    proxyContext.invokeMethod[StringJdiProxy](this, None, "+", Seq(Seq(proxy)))

}

object StringJdiProxy extends JdiProxyCompanion[StringJdiProxy, ObjectReference]
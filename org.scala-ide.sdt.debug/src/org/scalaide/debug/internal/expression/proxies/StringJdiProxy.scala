/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ObjectReference

/**
 * JdiProxy implementation for `java.lang.String`.
 */
case class StringJdiProxy(context: JdiContext, underlying: ObjectReference) extends JdiProxy {

  final def +(proxy: JdiProxy): StringJdiProxy =
    StringJdiProxy(context.invokeMethod[JdiProxy](this, None, "+", Seq(Seq(proxy))))

}

object StringJdiProxy extends JdiProxyCompanion[StringJdiProxy]
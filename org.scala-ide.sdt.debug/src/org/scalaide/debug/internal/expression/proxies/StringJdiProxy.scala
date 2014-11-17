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


  override protected def specialFunction(name: String, args: Seq[Any]): Option[JdiProxy] = name match {
    case "+" => args match {
      case Seq(proxy: JdiProxy) =>
        Some(proxyContext.invokeMethod(this, None, "+", Seq(proxy)))
      case _ => None
    }
    case _ => None
  }

  def stringValue: String = __underlying.toString().drop(1).dropRight(1) // drop " at the beginning and end
}

object StringJdiProxy extends JdiProxyCompanion[StringJdiProxy, ObjectReference]
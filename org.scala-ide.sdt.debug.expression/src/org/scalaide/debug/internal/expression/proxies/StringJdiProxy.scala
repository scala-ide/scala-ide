/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.StringReference

/**
 * JdiProxy implementation for `java.lang.String`.
 */
case class StringJdiProxy(override val __context: JdiContext, override val __value: StringReference)
    extends ObjectJdiProxy(__context, __value) {

  override protected def callSpecialMethod(name: String, args: Seq[Any]): Option[JdiProxy] = (name, args) match {
    case ("+", Seq(proxy: JdiProxy)) =>
      Some(__context.invokeMethod(this, None, "+", Seq(proxy)))
    case _ => None
  }

  def stringValue: String = __value.toString().drop(1).dropRight(1) // drop " at the beginning and end
}

object StringJdiProxy extends JdiProxyCompanion[StringJdiProxy, StringReference]

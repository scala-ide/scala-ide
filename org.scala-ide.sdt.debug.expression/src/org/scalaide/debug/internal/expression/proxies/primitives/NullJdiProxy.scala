/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ObjectReference

/**
 * JdiProxy implementation for `null`.
 */
case class NullJdiProxy(proxyContext: JdiContext) extends JdiProxy {

  private def fail(methodName: String): Nothing = throw new NullPointerException(s"Cannot call $methodName method on null.")

  override def __underlying: ObjectReference = null

  /** Implementation of method application. */
  override def applyDynamic(name: String)(args: Any*): JdiProxy = fail(name)

  /** Implementation of field selection. */
  override def selectDynamic(name: String): JdiProxy = fail(name)

  /** Implementation of variable mutation. */
  override def updateDynamic(name: String)(value: Any): Unit = fail(name)
}
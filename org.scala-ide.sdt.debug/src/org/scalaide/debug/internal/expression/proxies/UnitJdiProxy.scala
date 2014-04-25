/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext

import com.sun.jdi.ObjectReference

/**
 * JdiProxy implementation for `java.lang.Void`.
 */
case class UnitJdiProxy(context: JdiContext) extends JdiProxy {

  private def fail: Nothing = throw new UnsupportedOperationException("There are no methods on Unit.")

  override def underlying: ObjectReference = fail

  /** Implementation of method application. */
  override def applyDynamic(name: String)(args: Any*): JdiProxy = fail

  /** Implementation of field selection. */
  override def selectDynamic(name: String): JdiProxy = fail

  /** Implementation of variable mutation. */
  override def updateDynamic(name: String)(value: Any): Unit = fail
}
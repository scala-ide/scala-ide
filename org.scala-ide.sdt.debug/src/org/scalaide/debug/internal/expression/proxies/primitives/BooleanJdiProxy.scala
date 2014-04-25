/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.operations.BooleanComparison
import org.scalaide.debug.internal.expression.proxies.primitives.operations.LogicalOperations

import com.sun.jdi.BooleanValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * JdiProxy implementation for `bool`, `scala.Boolean` and `java.lang.Boolean`.
 */
case class BooleanJdiProxy(context: JdiContext, underlying: ObjectReference)
  extends BoxedJdiProxy[Boolean, BooleanJdiProxy](BooleanJdiProxy)
  with LogicalOperations
  with BooleanComparison {

  final def booleanValue: Boolean = primitive.asInstanceOf[BooleanValue].value
}

object BooleanJdiProxy extends BoxedJdiProxyCompanion[Boolean, BooleanJdiProxy](JavaBoxed.Boolean, JavaPrimitives.boolean) {
  protected def mirror(value: Boolean, context: JdiContext): Value = context.mirrorOf(value)
}
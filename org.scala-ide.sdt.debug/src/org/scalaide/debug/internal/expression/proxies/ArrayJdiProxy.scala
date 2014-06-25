/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference

/**
 * JdiProxy implementation for java `Array`s.
 */
case class ArrayJdiProxy(context: JdiContext, underlying: ArrayReference) extends JdiProxy {

  /** The element at given index. */
  final def apply(i: IntJdiProxy): JdiProxy = context.valueProxy(underlying.getValue(i.primitiveValue))

  /** Update the element at given index. */
  final def update(i: IntJdiProxy, value: JdiProxy): UnitJdiProxy = {
    underlying.setValue(i.primitiveValue, value.underlying)
    UnitJdiProxy(context)
  }

  /** Update the element at given index. Overloaded for arrays of primitive types. */
  final def update(i: IntJdiProxy, value: BoxedJdiProxy[_, _]): UnitJdiProxy = {
    underlying.setValue(i.primitiveValue, value.primitive)
    UnitJdiProxy(context)
  }

  /** Length of underlying array. */
  final def length: IntJdiProxy = context.proxy(underlying.length)

}

object ArrayJdiProxy extends JdiProxyCompanion[ArrayJdiProxy, ArrayReference]
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference

/**
 * JdiProxy implementation for java `Array`s.
 */
case class ArrayJdiProxy[ProxyType <: JdiProxy](proxyContext: JdiContext, __underlying: ArrayReference) extends JdiProxy {

  /** The element at given index. */
  final def apply(i: IntJdiProxy): ProxyType = proxyContext.valueProxy(__underlying.getValue(i.primitiveValue)).asInstanceOf[ProxyType]

  /** Update the element at given index. */
  final def update(i: IntJdiProxy, value: JdiProxy): UnitJdiProxy = {
    __underlying.setValue(i.primitiveValue, value.__underlying)
    UnitJdiProxy(proxyContext)
  }

  /** Update the element at given index. Overloaded for arrays of primitive types. */
  final def update(i: IntJdiProxy, value: BoxedJdiProxy[_, _]): UnitJdiProxy = {
    __underlying.setValue(i.primitiveValue, value.primitive)
    UnitJdiProxy(proxyContext)
  }

  /** Length of underlying array. */
  final def length: IntJdiProxy = proxyContext.proxy(__underlying.length)

}

object ArrayJdiProxy {

  /** Creates a JdiProxy based on existing one */
  def apply[ProxyType <: JdiProxy](on: JdiProxy): ArrayJdiProxy[ProxyType] =
    JdiProxyCompanion.unwrap[ArrayJdiProxy[ProxyType], ArrayReference](on)(this.apply(_, _))
}
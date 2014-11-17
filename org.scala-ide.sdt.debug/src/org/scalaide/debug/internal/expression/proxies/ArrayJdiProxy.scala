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

  override protected def specialFunction(name: String, args: Seq[Any]): Option[JdiProxy] = name match {
    case "length" if args.isEmpty => Some(proxyContext.proxy(__underlying.length))
    case "update" => args match {
      case Seq(i: IntJdiProxy, value: BoxedJdiProxy[_, _]) =>
        __underlying.setValue(i._IntMirror, value.primitive)
        Some(UnitJdiProxy(proxyContext))
      case Seq(i: IntJdiProxy, value: JdiProxy) =>
        __underlying.setValue(i._IntMirror, value.__underlying)
        Some(UnitJdiProxy(proxyContext))
      case _ => None
    }
    case "apply" => args match {
      case Seq(i: IntJdiProxy) =>
        Some(proxyContext.valueProxy(__underlying.getValue(i._IntMirror)))
      case _ => None
    }
    case _ => None

  }
}

object ArrayJdiProxy {

  /** Creates a JdiProxy based on existing one */
  def apply[ProxyType <: JdiProxy](on: JdiProxy): ArrayJdiProxy[ProxyType] =
    JdiProxyCompanion.unwrap[ArrayJdiProxy[ProxyType], ArrayReference](on)(this.apply(_, _))
}
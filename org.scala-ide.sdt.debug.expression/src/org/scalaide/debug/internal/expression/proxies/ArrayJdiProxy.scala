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

  override protected def callSpecialMethod(name: String, args: Seq[Any]): Option[JdiProxy] = name match {
    case "length" if args.isEmpty => callLenght()
    case "update" => callUpdate(args)
    case "apply" => callApply(args)
    case _ => None
  }

  private def callLenght() = Some(proxyContext.proxy(__underlying.length))

  private def callUpdate(args: Seq[Any]) = args match {
    case Seq(i: IntJdiProxy, value: BoxedJdiProxy[_, _]) =>
      __underlying.setValue(i.__value[Int], value.primitive)
      Some(UnitJdiProxy(proxyContext))
    case Seq(i: IntJdiProxy, value: JdiProxy) =>
      __underlying.setValue(i.__value[Int], value.__underlying)
      Some(UnitJdiProxy(proxyContext))
    case _ => None
  }

  private def callApply(args: Seq[Any]) =
    args match {
      case Seq(i: IntJdiProxy) =>
        Some(proxyContext.valueProxy(__underlying.getValue(i.__value[Int])))
      case _ => None
    }
}

object ArrayJdiProxy {

  /** Creates a JdiProxy based on existing one */
  def apply[ProxyType <: JdiProxy](on: JdiProxy): ArrayJdiProxy[ProxyType] =
    JdiProxyCompanion.unwrap[ArrayJdiProxy[ProxyType], ArrayReference](on)(this.apply(_, _))
}

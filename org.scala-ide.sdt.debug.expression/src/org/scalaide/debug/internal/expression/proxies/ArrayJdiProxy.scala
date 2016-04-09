/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType

/**
 * JdiProxy implementation for java `Array`s.
 */
case class ArrayJdiProxy[ProxyType <: JdiProxy](override val __context: JdiContext, override val __value: ArrayReference)
  extends ObjectJdiProxy(__context, __value) {

  override protected def callSpecialMethod(name: String, args: Seq[Any]): Option[JdiProxy] = name match {
    case "length" if args.isEmpty => callLenght()
    case "update" => callUpdate(args)
    case "apply" => callApply(args)
    case _ => None
  }

  override def __type: ArrayType = __value.referenceType.asInstanceOf[ArrayType]

  private def callLenght() = Some(__context.proxy(__value.length))

  private def callUpdate(args: Seq[Any]) = args match {
    case Seq(i: IntJdiProxy, value: JdiProxy) =>
      __value.setValue(i.__value.value, value.__value)
      Some(UnitJdiProxy(__context))
    case _ => None
  }

  private def callApply(args: Seq[Any]) =
    args match {
      case Seq(i: IntJdiProxy) =>
        Some(__context.valueProxy(__value.getValue(i.__value.value)))
      case _ => None
    }
}

object ArrayJdiProxy {

  /** Creates a JdiProxy based on existing one */
  def apply[ProxyType <: JdiProxy](on: JdiProxy): ArrayJdiProxy[ProxyType] =
    JdiProxyCompanion.unwrap[ArrayJdiProxy[ProxyType], ArrayReference](on)(this.apply(_, _))
}

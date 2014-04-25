/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import org.scalaide.debug.internal.expression.proxies.JdiProxy
import com.sun.jdi.StringReference
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import scala.reflect.NameTransformer
import org.scalaide.debug.internal.expression.proxies.UnitJdiProxy

/**
 * Part of JdiContext responsible for converting proxies to their string representations.
 */
trait Stringifier {
  self: MethodInvoker =>

  /** Calls `toString` on given proxy, returns jdi String reference. */
  final def callToString(proxy: JdiProxy): StringReference =
    invokeUnboxed[StringReference](proxy, "toString", Seq.empty)

  /**
   * Calls `toString` on given proxy, returns StringJdiProxy.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  final def stringify(proxy: JdiProxy): StringJdiProxy =
    this.invokeMethod[StringJdiProxy](proxy, "toString")

  /**
   * String representation of given proxy. Contains value and type.
   */
  def show(proxy: JdiProxy, withType: Boolean = true): String = proxy match {
    case unit: UnitJdiProxy => "() (of type: scala.Unit)"
    case _ => {
      val stringValue = callToString(proxy).value
      val underlyingType = proxy.underlying.referenceType.name
      val typeDecoded = NameTransformer.decode(underlyingType)
      if (withType) s"$stringValue (of type: $typeDecoded)" else stringValue
    }
  }
}
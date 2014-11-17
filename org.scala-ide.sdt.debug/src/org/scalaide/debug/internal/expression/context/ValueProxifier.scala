/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.annotation.implicitNotFound
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy
import org.scalaide.debug.internal.expression.proxies.{JdiProxy, StringJdiProxy}

/**
 * Type class for providing primitive proxies.
 */
@implicitNotFound("Proxying type $T is not supported.")
sealed trait ValueProxifier[ValueType] {
  def apply(value: ValueType, context: JdiContext): JdiProxy
}

/**
 * Instances of ValueProxifier for `java.lang.String` and Java primitives.
 */
object ValueProxifier {

  /** Helps with ValueProxifier creation */
  private def apply[ValueType](f: (ValueType, JdiContext) => JdiProxy) =
    new ValueProxifier[ValueType] {
      def apply(value: ValueType, context: JdiContext) = f(value, context)
    }

  implicit val stringProxifier = ValueProxifier {
    (value: String, context: JdiContext) => StringJdiProxy(context, context.mirrorOf(value))
  }

  implicit val byteProxifier = ValueProxifier(ByteJdiProxy.fromPrimitive)

  implicit val shortProxifier = ValueProxifier(ShortJdiProxy.fromPrimitive)

  implicit val intProxifier = ValueProxifier(IntJdiProxy.fromPrimitive)

  implicit val charProxifier = ValueProxifier(CharJdiProxy.fromPrimitive)

  implicit val doubleProxifier = ValueProxifier(DoubleJdiProxy.fromPrimitive)

  implicit val floatProxifier = ValueProxifier(FloatJdiProxy.fromPrimitive)

  implicit val longProxifier = ValueProxifier(LongJdiProxy.fromPrimitive)

  implicit val booleanProxifier = ValueProxifier(BooleanJdiProxy.fromPrimitive)

}
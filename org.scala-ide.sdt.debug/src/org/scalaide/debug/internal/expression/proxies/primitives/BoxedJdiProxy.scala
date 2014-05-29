/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.ScalaPrimitivesUnified
import org.scalaide.debug.internal.expression.ScalaRichTypes
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxyCompanion
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Base for all boxed primitives proxies.
 *
 * @tparam Primitive type to proxy
 * @tparam Proxy type of proxy
 * @param companion companion object for this proxy
 */
abstract class BoxedJdiProxy[Primitive, Proxy <: BoxedJdiProxy[Primitive, Proxy]](companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends JdiProxy { self: Proxy =>

  /** Underlying primitive value from this proxy. */
  final def primitive: Value = companion.primitive(this)

  /** Underlying primitive name. */
  final def primitiveName: String = companion.unboxedName

  /** Implementation of string addition. */
  def +(proxy: StringJdiProxy): StringJdiProxy =
    StringJdiProxy(context.invokeMethod[JdiProxy](this, None, "+", Seq(Seq(proxy))))
}

/**
 * Base for companions of [[org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy]].
 *
 * It requires one to implement `mirror` method.
 *
 * @tparam Primitive type to proxy
 * @tparam Proxy type of proxy
 * @param boxedName name of boxed type (for example 'java.lang.Character')
 * @param unboxedName name of unboxed type (for example 'char')
 */
abstract class BoxedJdiProxyCompanion[Primitive, Proxy <: BoxedJdiProxy[Primitive, Proxy]](val boxedName: String, val unboxedName: String)
  extends JdiProxyCompanion[Proxy, ObjectReference] {

  /** Creates a mirror of primitive value in debug context. */
  protected def mirror(value: Primitive, context: JdiContext): Value

  /**
   * Underlying primitive value using JDI.
   *
   * @param proxy proxy to get value from
   */
  final def primitive(proxy: Proxy): Value = {
    val method = proxy.objectType.methodsByName(unboxedName + "Value").head

    proxy.context.invokeUnboxed[Value](proxy, None, method.name, Seq.empty, Seq.empty)
  }

  /** Creates proxy from primitive using proxy context */
  final def fromPrimitive(value: Primitive, context: JdiContext): Proxy =
    fromValue(mirror(value, context), context)

  /** Creates proxy from value using proxy context */
  final def fromValue(value: Value, context: JdiContext): Proxy =
    context.fromValue(value, this)
}

private[expression] object BoxedJdiProxy {

  /** Maps java and scala primitive type names to appriopriate proxies. */
  val primitiveToProxyMap = Map(
    ScalaPrimitivesUnified.Byte -> classOf[ByteJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Short -> classOf[ShortJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Int -> classOf[IntJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Long -> classOf[LongJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Double -> classOf[DoubleJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Float -> classOf[FloatJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Char -> classOf[CharJdiProxy].getSimpleName,
    ScalaPrimitivesUnified.Boolean -> classOf[BooleanJdiProxy].getSimpleName,

    ScalaOther.unitType -> classOf[UnitJdiProxy].getSimpleName,

    ScalaRichTypes.Boolean -> classOf[BooleanJdiProxy].getSimpleName,
    ScalaRichTypes.Byte -> classOf[ByteJdiProxy].getSimpleName,
    ScalaRichTypes.Char -> classOf[CharJdiProxy].getSimpleName,
    ScalaRichTypes.Double -> classOf[DoubleJdiProxy].getSimpleName,
    ScalaRichTypes.Float -> classOf[FloatJdiProxy].getSimpleName,
    ScalaRichTypes.Int -> classOf[IntJdiProxy].getSimpleName,
    ScalaRichTypes.Long -> classOf[LongJdiProxy].getSimpleName,
    ScalaRichTypes.Short -> classOf[ShortJdiProxy].getSimpleName,

    JavaBoxed.Byte -> classOf[ByteJdiProxy].getSimpleName,
    JavaBoxed.Short -> classOf[ShortJdiProxy].getSimpleName,
    JavaBoxed.Integer -> classOf[IntJdiProxy].getSimpleName,
    JavaBoxed.Long -> classOf[LongJdiProxy].getSimpleName,
    JavaBoxed.Double -> classOf[DoubleJdiProxy].getSimpleName,
    JavaBoxed.Float -> classOf[FloatJdiProxy].getSimpleName,
    JavaBoxed.Character -> classOf[CharJdiProxy].getSimpleName,
    JavaBoxed.Boolean -> classOf[BooleanJdiProxy].getSimpleName,
    JavaBoxed.String -> classOf[StringJdiProxy].getSimpleName)
}
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

  /** Maps java and scala primitive type names to appropriate proxies. */
  val primitiveToProxyMap = Map(
    ScalaPrimitivesUnified.Byte -> classOf[ByteJdiProxy],
    ScalaPrimitivesUnified.Short -> classOf[ShortJdiProxy],
    ScalaPrimitivesUnified.Int -> classOf[IntJdiProxy],
    ScalaPrimitivesUnified.Long -> classOf[LongJdiProxy],
    ScalaPrimitivesUnified.Double -> classOf[DoubleJdiProxy],
    ScalaPrimitivesUnified.Float -> classOf[FloatJdiProxy],
    ScalaPrimitivesUnified.Char -> classOf[CharJdiProxy],
    ScalaPrimitivesUnified.Boolean -> classOf[BooleanJdiProxy],

    ScalaOther.unitType -> classOf[UnitJdiProxy],

    ScalaRichTypes.Boolean -> classOf[BooleanJdiProxy],
    ScalaRichTypes.Byte -> classOf[ByteJdiProxy],
    ScalaRichTypes.Char -> classOf[CharJdiProxy],
    ScalaRichTypes.Double -> classOf[DoubleJdiProxy],
    ScalaRichTypes.Float -> classOf[FloatJdiProxy],
    ScalaRichTypes.Int -> classOf[IntJdiProxy],
    ScalaRichTypes.Long -> classOf[LongJdiProxy],
    ScalaRichTypes.Short -> classOf[ShortJdiProxy],

    JavaBoxed.Byte -> classOf[ByteJdiProxy],
    JavaBoxed.Short -> classOf[ShortJdiProxy],
    JavaBoxed.Integer -> classOf[IntJdiProxy],
    JavaBoxed.Long -> classOf[LongJdiProxy],
    JavaBoxed.Double -> classOf[DoubleJdiProxy],
    JavaBoxed.Float -> classOf[FloatJdiProxy],
    JavaBoxed.Character -> classOf[CharJdiProxy],
    JavaBoxed.Boolean -> classOf[BooleanJdiProxy],
    JavaBoxed.String -> classOf[StringJdiProxy]).mapValues(_.getSimpleName)
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxyCompanion
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.scalaide.debug.internal.expression.Names

/**
 * Base for all boxed primitives proxies.
 *
 * @tparam Primitive type to proxy
 * @tparam Proxy type of proxy
 * @param companion companion object for this proxy
 */
abstract class BoxedJdiProxy[Primitive, Proxy <: BoxedJdiProxy[Primitive, Proxy]](companion: BoxedJdiProxyCompanion[Primitive, Proxy])
  extends JdiProxy {
  self: Proxy =>

  /** Underlying primitive value from this proxy. */
  final def primitive: Value = companion.primitive(this)

  /** Underlying primitive name. */
  final def primitiveName: String = companion.unboxedName

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
    val method = proxy.referenceType.methodsByName(unboxedName + "Value").head

    proxy.proxyContext.invokeUnboxed[Value](proxy, None, method.name)
  }

  /** Creates proxy from primitive using proxy context */
  final def fromPrimitive(value: Primitive, context: JdiContext): Proxy =
    fromPrimitiveValue(mirror(value, context), context)

  /** Creates proxy from value using proxy context */
  final def fromPrimitiveValue(value: Value, context: JdiContext): Proxy =
    context.fromPrimitiveValue(value, this)
}

private[expression] object BoxedJdiProxy {

  /** Maps java and scala primitive type names to appropriate proxies. */
  def primitiveToProxy(primitive: String): Option[String] = {
    val prefix =
      if (primitive.head.isUpper && !primitive.startsWith("scala") && !primitive.startsWith("java.lang")) "scala."
      else ""

    primitiveToProxyMap.get(prefix + primitive)
  }

  private val primitiveToProxyMap = Map(
    Scala.primitives.Byte -> classOf[ByteJdiProxy],
    Scala.primitives.Short -> classOf[ShortJdiProxy],
    Scala.primitives.Int -> classOf[IntJdiProxy],
    Scala.primitives.Long -> classOf[LongJdiProxy],
    Scala.primitives.Double -> classOf[DoubleJdiProxy],
    Scala.primitives.Float -> classOf[FloatJdiProxy],
    Scala.primitives.Char -> classOf[CharJdiProxy],
    Scala.primitives.Boolean -> classOf[BooleanJdiProxy],

    Scala.unitType -> classOf[UnitJdiProxy],
    Scala.nullType -> classOf[NullJdiProxy],

    Scala.rich.Boolean -> classOf[BooleanJdiProxy],
    Scala.rich.Byte -> classOf[ByteJdiProxy],
    Scala.rich.Char -> classOf[CharJdiProxy],
    Scala.rich.Double -> classOf[DoubleJdiProxy],
    Scala.rich.Float -> classOf[FloatJdiProxy],
    Scala.rich.Int -> classOf[IntJdiProxy],
    Scala.rich.Long -> classOf[LongJdiProxy],
    Scala.rich.Short -> classOf[ShortJdiProxy],

    Java.boxed.Byte -> classOf[ByteJdiProxy],
    Java.boxed.Short -> classOf[ShortJdiProxy],
    Java.boxed.Integer -> classOf[IntJdiProxy],
    Java.boxed.Long -> classOf[LongJdiProxy],
    Java.boxed.Double -> classOf[DoubleJdiProxy],
    Java.boxed.Float -> classOf[FloatJdiProxy],
    Java.boxed.Character -> classOf[CharJdiProxy],
    Java.boxed.Boolean -> classOf[BooleanJdiProxy],
    Java.boxed.String -> classOf[StringJdiProxy]).mapValues(_.getSimpleName)
}
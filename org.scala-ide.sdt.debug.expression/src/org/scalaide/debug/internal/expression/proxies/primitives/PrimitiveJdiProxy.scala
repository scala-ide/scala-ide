/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.primitives

import scala.collection.JavaConverters._

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxyCompanion
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue

/**
 * Base for all primitive proxies.
 *
 * @tparam Primitive type to proxy
 * @tparam ProxyType type of proxy
 * @param companion companion object for this proxy
 */
abstract class PrimitiveJdiProxy[Primitive, ProxyType <: PrimitiveJdiProxy[Primitive, ProxyType, ValueType], ValueType <: PrimitiveValue](
  companion: PrimitiveJdiProxyCompanion[Primitive, ProxyType, ValueType])
    extends JdiProxy {
  self: ProxyType =>

  /** Underlying primitive value from this proxy. */
  override def __value: ValueType

  final def boxed: ObjectReference = {
    val boxedClass: ClassType = __context.classByName(companion.name.javaBoxed)

    val boxingMethod: Method = boxedClass
      .methodsByName("valueOf").asScala
      .filter(_.argumentTypeNames.asScala.toSeq == Seq(companion.name.java))
      .head

    boxedClass.invokeMethod(__context.currentThread(), boxingMethod, List(__value)).asInstanceOf[ObjectReference]
  }

  /** Underlying primitive name. */
  final def primitiveName: String = companion.name.java

  /** Underlying boxed name */
  final def boxedName: String = companion.name.javaBoxed

  override final protected[expression] def genericThisType: Option[String] = Some(companion.name.scalaRich)
}

/**
 * Base for companions of [[org.scalaide.debug.internal.expression.proxies.primitives.PrimitiveJdiProxy]].
 *
 * It requires one to implement `mirror` method.
 *
 * @tparam Primitive type to proxy
 * @tparam ProxyType type of proxy
 * @param boxedName name of boxed type (for example 'java.lang.Character')
 * @param unboxedName name of unboxed type (for example 'char')
 */
abstract class PrimitiveJdiProxyCompanion[Primitive, ProxyType <: PrimitiveJdiProxy[Primitive, ProxyType, ValueType], ValueType <: PrimitiveValue](
  val name: TypeNames.Primitive)
    extends JdiProxyCompanion[ProxyType, ValueType] {

  /** Creates a mirror of primitive value in debug context. */
  protected def mirror(value: Primitive, context: JdiContext): ValueType

  /** Creates proxy from primitive using proxy context */
  final def fromPrimitive(value: Primitive, context: JdiContext): ProxyType =
    apply(context, mirror(value, context))
}

private[expression] object PrimitiveJdiProxy {

  /** Maps java and scala primitive type names to appropriate proxies. */
  def primitiveToProxy(primitiveType: String): String = {
    val prefix =
      if (primitiveType.head.isUpper && !primitiveType.startsWith("scala") && !primitiveType.startsWith("java.lang")) "scala."
      else ""

    primitiveToProxyMap(prefix + primitiveType)
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
    Java.String -> classOf[StringJdiProxy]).mapValues(_.getSimpleName)
}

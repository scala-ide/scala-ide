/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.SimpleJdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallClassJdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallInterfaceJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxyCompanion
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.ShortValue
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

/**
 * Part of JdiContext responsible for creating all kind of proxies.
 */
private[context] trait Proxyfier {
  self: JdiContext =>

  /**
   * Creates a proxy for a Scala object with given name.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def objectOrStaticCallProxy(name: String): JdiProxy =
    tryObjectByName(name) match {
      case Some(objRef) => SimpleJdiProxy(this, objRef)
      case None => staticCallProxy(name)
    }

  private def staticCallProxy(name: String) =
    tryClassByName(name) match {
      case Some(classType) =>
        StaticCallClassJdiProxy(this, classType)
      case None =>
        val interfaceType = interfaceByName(name,
          onNotFound = realTypeName => throw new RuntimeException(s"Class, object or interface not found: $realTypeName"))
        StaticCallInterfaceJdiProxy(this, interfaceType)
    }

  /**
   * Creates a proxy for a value with given name.
   * Value of that name have to exist in scope or running program.
   *
   * @throws NoSuchFieldError if field is not found.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def valueProxy(name: String): JdiProxy = {
    val value = valueFromFrame(this.currentFrame(), name)
      .getOrElse(throw new NoSuchFieldError(s"There is no static field named $name"))
    valueProxy(value)
  }

  /** Creates a proxy for a given value. */
  final def valueProxy(value: Value): JdiProxy = value match {
    case null => NullJdiProxy(this)

    case arrayReference: ArrayReference =>
      ArrayJdiProxy(this, arrayReference)
    case objectReference: ObjectReference =>
      val name = objectReference.`type`.name
      javaBoxedMap
        .get(name)
        .map(_.apply(this, objectReference))
        .getOrElse(SimpleJdiProxy(this, objectReference))

    case value: ByteValue => ByteJdiProxy.fromPrimitiveValue(value, this)
    case value: ShortValue => ShortJdiProxy.fromPrimitiveValue(value, this)
    case value: IntegerValue => IntJdiProxy.fromPrimitiveValue(value, this)
    case value: FloatValue => FloatJdiProxy.fromPrimitiveValue(value, this)
    case value: DoubleValue => DoubleJdiProxy.fromPrimitiveValue(value, this)
    case value: CharValue => CharJdiProxy.fromPrimitiveValue(value, this)
    case value: BooleanValue => BooleanJdiProxy.fromPrimitiveValue(value, this)
    case value: LongValue => LongJdiProxy.fromPrimitiveValue(value, this)
    case value: VoidValue => UnitJdiProxy(this)

    case v => throw new UnsupportedOperationException("not supported primitive class: " + v.getClass.getName)
  }

  /**
   * Creates a proxy for boxed primitive from value.
   * See also [[org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxyCompanion]].
   *
   * @param value to proxy
   * @param companion used for choosing right primitive type
   */
  final def fromPrimitiveValue[Primitive, Proxy <: BoxedJdiProxy[Primitive, Proxy]](value: Value,
    companion: BoxedJdiProxyCompanion[Primitive, Proxy]): Proxy = {

    val boxedClass = this.classByName(companion.boxedName)

    val boxingMethod = boxedClass
      .methodsByName("valueOf")
      .filter(_.argumentTypeNames().toSeq == Seq(companion.unboxedName))
      .head

    val boxedValue: ObjectReference =
      boxedClass.invokeMethod(this.currentThread, boxingMethod, List(value),
        ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[ObjectReference]

    companion.apply(this, boxedValue)
  }

  /**
   * Creates a proxy for `this` object.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  final def thisObjectProxy(): JdiProxy = valueProxy(currentFrame().thisObject())

  /**
   * Creates a proxy for given value (typed by user, not `com.sun.jdi.Value`).
   * Works for all Java boxed primitives and `java.lang.String`.
   *
   * Implementation uses `ValueProxifier` type class.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   *
   * @param value value to create proxy from
   */
  def proxy(value: Any): JdiProxy = value match {
    case v: Byte => ByteJdiProxy.fromPrimitive(v, this)
    case v: Int => IntJdiProxy.fromPrimitive(v, this)
    case v: Short => ShortJdiProxy.fromPrimitive(v, this)
    case v: Char => CharJdiProxy.fromPrimitive(v, this)
    case v: Double => DoubleJdiProxy.fromPrimitive(v, this)
    case v: Float => FloatJdiProxy.fromPrimitive(v, this)
    case v: Long => LongJdiProxy.fromPrimitive(v, this)
    case v: Boolean => BooleanJdiProxy.fromPrimitive(v, this)
    case v: String => StringJdiProxy(this, mirrorOf(v))
  }

  /**
   * Mirrors given value in context of running debug.
   * Works for all Java boxed primitives and `java.lang.String`.
   *
   * Implementation uses `ValueMirror` type class.
   *
   * @param value value to mirror
   * @param mirror type class instance
   * @tparam ValueType type of value
   * @tparam ReturnType return type (StringReference for Strings, <boxed primitive>Value for boxed primitives
   */
  final def mirrorOf[ValueType, ReturnType](value: ValueType)(implicit mirror: ValueMirror[ValueType, ReturnType]): ReturnType =
    mirror.mirrorOf(value, jvm)

  /**
   * Get field with given name from given proxy.
   * @param proxy - proxy to get value from
   * @param name - name of value
   * @throws NoSuchFieldError if field is not found.
   */
  final def proxyForField(proxy: JdiProxy, name: String): JdiProxy = {
    val underlying = proxy.__underlying

    def noSuchFieldError = s"Object of type ${underlying.referenceType()} has no field named $name"
    val field = Option(underlying.referenceType().fieldByName(name))
      .getOrElse(throw new NoSuchFieldError(noSuchFieldError))
    valueProxy(underlying.getValue(field))
  }

  /** Maps java types to corresponding proxies */
  private val javaBoxedMap = Map(
    Java.boxed.String -> StringJdiProxy,
    Java.boxed.Boolean -> BooleanJdiProxy,
    Java.boxed.Byte -> ByteJdiProxy,
    Java.boxed.Short -> ShortJdiProxy,
    Java.boxed.Integer -> IntJdiProxy,
    Java.boxed.Double -> DoubleJdiProxy,
    Java.boxed.Float -> FloatJdiProxy,
    Java.boxed.Character -> CharJdiProxy,
    Java.boxed.Long -> LongJdiProxy)
}
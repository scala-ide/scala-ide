/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.SimpleJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.UnitJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxyCompanion
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy

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
   * Creates a proxy for a scala object with given name.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def objectProxy(name: String): JdiProxy =
    SimpleJdiProxy(this, objectByName(name))

  /**
   * Creates a proxy for a value with given name.
   * Value of that name have to exist in scope or running program.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def valueProxy(name: String): JdiProxy =
    valueProxy(valueFromFrame(topFrame, name)
      .getOrElse(throw new RuntimeException(s"Value with name: $name not found.")))

  /** Creates a proxy for a given value. */
  final def valueProxy(value: Value): JdiProxy = value match {
    case objectReference: ObjectReference =>
      val name = objectReference.`type`.name
      javaBoxedMap
        .get(name)
        .map(_.apply(this, objectReference))
        .getOrElse(SimpleJdiProxy(this, objectReference))

    case value: ByteValue => ByteJdiProxy.fromValue(value, this)
    case value: ShortValue => ShortJdiProxy.fromValue(value, this)
    case value: IntegerValue => IntJdiProxy.fromValue(value, this)
    case value: FloatValue => FloatJdiProxy.fromValue(value, this)
    case value: DoubleValue => DoubleJdiProxy.fromValue(value, this)
    case value: CharValue => CharJdiProxy.fromValue(value, this)
    case value: BooleanValue => BooleanJdiProxy.fromValue(value, this)
    case value: LongValue => LongJdiProxy.fromValue(value, this)
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
  final def fromValue[Primitive, Proxy <: BoxedJdiProxy[Primitive, Proxy]](value: Value,
    companion: BoxedJdiProxyCompanion[Primitive, Proxy]): Proxy = {

    val boxedClass = this.classByName(companion.boxedName)

    val boxingMethod = boxedClass
      .methodsByName("valueOf")
      .filter(_.argumentTypeNames().toSeq == Seq(companion.unboxedName))
      .head

    val boxedValue: ObjectReference =
      boxedClass.invokeMethod(this.currentThread, boxingMethod, List(value), 0).asInstanceOf[ObjectReference]

    companion.apply(this, boxedValue)
  }

  /**
   * Creates a proxy for `this` object.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  final def thisObjectProxy(): JdiProxy = valueProxy(currentThread.frames.head.thisObject)

  /**
   * Creates a proxy for given value. Works for all Java boxed primitives and `java.lang.String`.
   *
   * Implementation uses `ValueProxifier` type class.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   *
   * @param value value to create proxy from
   * @param proxifier type class instance
   * @tparam ValueType type of value
   * @tparam ProxyType type of proxy returned
   */
  def proxy[ValueType, ProxyType](value: ValueType)(implicit proxifier: ValueProxifier[ValueType, ProxyType]): ProxyType =
    proxifier(value, this)

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

  /** Maps java types to corresponding proxies */
  private val javaBoxedMap = Map(
    JavaBoxed.String -> StringJdiProxy,
    JavaBoxed.Boolean -> BooleanJdiProxy,
    JavaBoxed.Byte -> ByteJdiProxy,
    JavaBoxed.Short -> ShortJdiProxy,
    JavaBoxed.Integer -> IntJdiProxy,
    JavaBoxed.Double -> DoubleJdiProxy,
    JavaBoxed.Float -> FloatJdiProxy,
    JavaBoxed.Character -> CharJdiProxy,
    JavaBoxed.Long -> LongJdiProxy)
}
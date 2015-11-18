/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.annotation.implicitNotFound

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallClassJdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallInterfaceJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
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
import com.sun.jdi.ClassType
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
   * If you change its name, package or behavior, make sure to change it also.
   */
  def objectOrStaticCallProxy(name: String): JdiProxy =
    tryObjectByName(name) match {
      case Some(objRef) => ObjectJdiProxy(this, objRef)
      case None => staticCallProxy(name)
    }

  private def staticCallProxy(name: String) =
    tryByName[ClassType](name) match {
      case Some(classType) =>
        StaticCallClassJdiProxy(this, classType)
      case None =>
        val interfaceType = interfaceByName(name,
          onNotFound = realTypeName => throw new ClassNotFoundException(s"Class, object or interface not found: $realTypeName"))
        StaticCallInterfaceJdiProxy(this, interfaceType)
    }

  /**
   * Creates a proxy for a Scala `classOf` method.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def classOfProxy(name: String): JdiProxy = {
    import TypeNames._

    /** `true` if string matches either Java or Scala definition of array */
    def isArray(signature: String): Boolean = signature.startsWith("[")

    val result = convert(name, from = ScalaPrimitive, to = JavaBoxed) match {
      // primitives
      case Some(boxedName) =>
        val clsTpe = classByName(boxedName)
        clsTpe.getValue(clsTpe.fieldByName("TYPE"))
      // arrays
      case None if isArray(name) =>
        val fromSignature = TypeNames.arraySignatureToName(name)
        arrayByName(fromSignature).classObject
      // objects
      case None =>
        classByName(name).classObject
    }
    valueProxy(result)
  }

  /**
   * Creates a proxy for a value with given name.
   * Value of that name have to exist in scope or running program.
   *
   * @throws NoSuchFieldError if field is not found.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def valueProxy(name: String): JdiProxy = {
    val value = valueFromFrame(this.currentFrame(), name)
      .getOrElse(throw new NoSuchFieldError(s"There is no static field named $name"))
    valueProxy(value)
  }

  /** Creates a proxy for a given value. */
  final def valueProxy(value: Value): JdiProxy = value match {
    case null => NullJdiProxy(this)
    case arrayReference: ArrayReference => ArrayJdiProxy(this, arrayReference)
    case objectReference: ObjectReference => unbox(objectReference)
    case value: ByteValue => ByteJdiProxy(this, value)
    case value: ShortValue => ShortJdiProxy(this, value)
    case value: IntegerValue => IntJdiProxy(this, value)
    case value: FloatValue => FloatJdiProxy(this, value)
    case value: DoubleValue => DoubleJdiProxy(this, value)
    case value: CharValue => CharJdiProxy(this, value)
    case value: BooleanValue => BooleanJdiProxy(this, value)
    case value: LongValue => LongJdiProxy(this, value)
    case value: VoidValue => UnitJdiProxy(this)

    case v => throw new UnsupportedOperationException("not supported primitive class: " + v.getClass.getName)
  }

  /** Unboxes boxed primitives if needed. */
  private def unbox(objectReference: ObjectReference): JdiProxy = {
    val name = objectReference.`type`.name

    def primitiveValue = {
      import TypeNames._
      val unboxedName = convert(name, from = JavaBoxed, to = JavaPrimitive).get
      val method = methodOn(objectReference.referenceType, unboxedName + "Value", arity = 0)
      objectReference.invokeMethod(currentThread(), method, Seq())
    }

    name match {
      case Java.boxed.Boolean => BooleanJdiProxy(this, primitiveValue.asInstanceOf[BooleanValue])
      case Java.boxed.Byte => ByteJdiProxy(this, primitiveValue.asInstanceOf[ByteValue])
      case Java.boxed.Short => ShortJdiProxy(this, primitiveValue.asInstanceOf[ShortValue])
      case Java.boxed.Integer => IntJdiProxy(this, primitiveValue.asInstanceOf[IntegerValue])
      case Java.boxed.Double => DoubleJdiProxy(this, primitiveValue.asInstanceOf[DoubleValue])
      case Java.boxed.Float => FloatJdiProxy(this, primitiveValue.asInstanceOf[FloatValue])
      case Java.boxed.Character => CharJdiProxy(this, primitiveValue.asInstanceOf[CharValue])
      case Java.boxed.Long => LongJdiProxy(this, primitiveValue.asInstanceOf[LongValue])
      case other => ObjectJdiProxy(this, objectReference)
    }
  }

  /**
   * Creates a proxy for `this` object.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  final def thisObjectProxy(): ObjectJdiProxy =
    ObjectJdiProxy(this, currentFrame().thisObject())

  /**
   * Creates a proxy for given value (typed by user, not `com.sun.jdi.Value`).
   * Works for all Java boxed primitives and `java.lang.String`.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
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
  final def proxyForField(proxy: ObjectJdiProxy, name: String): JdiProxy = {
    def noSuchFieldError = s"Object of type ${proxy.__type} has no field named $name"
    val field = Option(proxy.__type.fieldByName(name))
      .getOrElse(throw new NoSuchFieldError(noSuchFieldError))
    valueProxy(proxy.__value.getValue(field))
  }

  /**
   * `proxyForField` flavor which casts result to `ObjectJdiProxy`.
   * @param proxy - proxy to get value from
   * @param name - name of value
   * @throws NoSuchFieldError if field is not found.
   */
  final def objectProxyForField(proxy: ObjectJdiProxy, name: String): ObjectJdiProxy =
    proxyForField(proxy, name).asInstanceOf[ObjectJdiProxy]
}

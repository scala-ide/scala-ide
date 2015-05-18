/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import scala.reflect.ClassTag
import scala.reflect.classTag

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.context.invoker.StringConcatenationMethod
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.PrimitiveJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Value

/**
 * The base for all proxies using JDI. Extends `scala.Dynamic` to proxy all method calls to debugged jvm using
 * [[org.scalaide.debug.internal.expression.proxies.JdiContext]].
 */
trait JdiProxy extends Dynamic {

  /**
   * Method for calling special methods (that are not simply translated to the method invocation) like update on arrays.
   * @return Some(result) if special method is called, None otherwise
   */
  protected def callSpecialMethod(name: String, args: Seq[Any]): Option[JdiProxy] = None

  /**
   * Context in which debug is running.
   * See [[org.scalaide.debug.internal.expression.proxies.JdiContext]] for more information.
   */
  protected[expression] def __context: JdiContext

  /** Value that lies under this proxy. */
  protected[expression] def __value: Value

  /** Type of underlying value. */
  protected[expression] def __type: Type = __value.`type`

  /** Applies mostly to primitives that are of Rich* class and should be boxed for some methods */
  protected def genericThisType: Option[String] = None

  /** Implementation of method application. */
  def applyDynamic(name: String)(args: Any*): JdiProxy = applyWithGenericType(name, genericThisType, args: _*)

  /** Implementation of method application. */
  def applyWithGenericType(name: String, thisType: Option[String], args: Any*): JdiProxy =
    callSpecialMethod(name, args).getOrElse {
      __context.invokeMethod(this, thisType.orElse(genericThisType), name, args.map(_.asInstanceOf[JdiProxy]))
    }

  /** Implementation of field selection. */
  def selectDynamic(name: String): JdiProxy =
    callSpecialMethod(name, Seq()).getOrElse {
      __context.invokeMethod(this, genericThisType, name, Seq())
    }

  /** Implementation of variable mutation. */
  def updateDynamic(name: String)(value: Any): UnitJdiProxy =
    __context.invokeMethod(this, genericThisType, s"${name}_=", Seq(value.asInstanceOf[JdiProxy])).asInstanceOf[UnitJdiProxy]

  /** Forwards equality to debugged jvm */
  def ==(other: JdiProxy): JdiProxy =
    __context.invokeMethod(this, genericThisType, "equals", Seq(other))

  /** Forwards inequality to debugged jvm */
  def !=(other: JdiProxy): JdiProxy =
    __context.proxy(!(this == other).__primitiveValue[Boolean])

  /**
   *  Method added to override standard "+" method that is defied for all objects and takes String as argument
   *  (compiler does not convert '+' to apply dynamic - it only complains about argument that is JdiProxy not String)
   */
  def +(v: JdiProxy): JdiProxy = {
    val invoker = new StringConcatenationMethod(this, "+", Seq(v), __context)
    __context.valueProxy(invoker().get)
  }

  /** Obtain primitive value from proxy of given type - implemented only in primitives */
  def __primitiveValue[I]: I = throw new UnsupportedOperationException(s"$this is not a primitive.")

  /** Wraps this proxy in ObjectJdiProxy if it refers to primitive type. */
  def __autoboxed: ObjectJdiProxy = this match {
    case primitive: PrimitiveJdiProxy[_, _, _] => ObjectJdiProxy(primitive.__context, primitive.boxed)
    case obj: ObjectJdiProxy => obj
  }
}

/**
 * Base for companion objects for [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
trait JdiProxyCompanion[ProxyType <: JdiProxy, Underlying <: Value] {

  /** Creates a JdiProxy for object using context */
  def apply(proxyContext: JdiContext, underlying: Underlying): ProxyType

  /**
   * Creates JdiProxy based on given one. Handles following cases:
   * - for `NullJdiProxy` returns instance of `Proxy` type with null as underlying
   * - for `Proxy` just returns it
   * - otherwise throws IllegalArgumentException
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def apply(on: JdiProxy)(implicit tag: ClassTag[ProxyType]): ProxyType = JdiProxyCompanion.unwrap(on)(this.apply)
}

object JdiProxyCompanion {

  /**
   * Private implementation for reducing code duplication in `JdiProxyCompanion` and `ArrayJdiProxy`.
   */
  private[proxies] def unwrap[ProxyType <: JdiProxy: ClassTag, Underlying <: ObjectReference](from: JdiProxy)(apply: (JdiContext, Underlying) => ProxyType): ProxyType = from match {
    case np: NullJdiProxy => apply(np.__context, np.__value.asInstanceOf[Underlying])
    case proxy: ProxyType => proxy
    case _ =>
      val className = classTag[ProxyType].runtimeClass.getSimpleName
      throw new IllegalArgumentException(s"Cannot create proxy of type: '$className' from: '$from'")
  }
}

/**
 * Simplest implementation of [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
class ObjectJdiProxy(val __context: JdiContext, val __value: ObjectReference) extends JdiProxy {
  override def __type: com.sun.jdi.ReferenceType = __value.referenceType
}

object ObjectJdiProxy {
  def apply(proxyContext: JdiContext, __value: ObjectReference): ObjectJdiProxy =
    new ObjectJdiProxy(proxyContext, __value)

  def unapply(proxy: ObjectJdiProxy): Option[(JdiContext, ObjectReference)] =
    Some(proxy.__context → proxy.__value)
}

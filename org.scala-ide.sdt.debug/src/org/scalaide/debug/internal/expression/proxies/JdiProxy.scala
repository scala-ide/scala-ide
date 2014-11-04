/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import scala.language.dynamics
import scala.reflect.ClassTag
import scala.reflect.classTag

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy

import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType

/**
 * The base for all proxies using JDI. Extends `scala.Dynamic` to proxy all method calls to debugged jvm using
 * [[org.scalaide.debug.internal.expression.proxies.JdiContext]].
 */
trait JdiProxy extends Dynamic {

  /**
   * Context in which debug is running.
   * See [[org.scalaide.debug.internal.expression.proxies.JdiContext]] for more information.
   */
  protected[expression] def proxyContext: JdiContext

  /** Reference that lies under this proxy. */
  protected[expression] def __underlying: ObjectReference

  /** Type of underlying reference. */
  protected[expression] def referenceType: ReferenceType = __underlying.referenceType

  /** Applies mostly to primitives that are of Rich* class and should be boxed for some methods */
  protected def genericThisType: Option[String] = None

  /** Implementation of method application. */
  def applyDynamic(name: String)(args: Any*): JdiProxy =
    proxyContext.invokeMethod[JdiProxy](this, genericThisType, name, Seq(args.map(_.asInstanceOf[JdiProxy])))

  /** Implementation of field selection. */
  def selectDynamic(name: String): JdiProxy =
    proxyContext.invokeMethod[JdiProxy](this, genericThisType, name, Seq())

  /** Implementation of variable mutation. */
  def updateDynamic(name: String)(value: Any): Unit =
    proxyContext.invokeMethod[JdiProxy](this, genericThisType, s"${name}_=", Seq(Seq(value.asInstanceOf[JdiProxy])))

  /** Forwards equality to debugged jvm */
  def ==(other: JdiProxy): BooleanJdiProxy =
    proxyContext.invokeMethod[BooleanJdiProxy](this, genericThisType, "equals", Seq(Seq(other)))

  /** Forwards inequality to debugged jvm */
  def !=(other: JdiProxy): BooleanJdiProxy =
    !(this == other)
}

/**
 * Base for companion objects for [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
trait JdiProxyCompanion[Proxy <: JdiProxy, Underlying <: ObjectReference] {

  /** Creates a JdiProxy for object using context */
  def apply(proxyContext: JdiContext, underlying: Underlying): Proxy

  /**
   * Creates JdiProxy based on given one. Handles following cases:
   * - for `NullJdiProxy` returns instance of `Proxy` type with null as underlying
   * - for `JdiProxyWrapper` returns inside of wrapper
   * - for `Proxy` just returns it
   * - otherwise throws IllegalArgumentException
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def apply(on: JdiProxy)(implicit tag: ClassTag[Proxy]): Proxy = JdiProxyCompanion.unwrap(on)(this.apply)
}

object JdiProxyCompanion {

  /**
   * Private implementation for reducing code duplication in `JdiProxyCompanion` and `ArrayJdiProxy`.
   */
  private[proxies] def unwrap[Proxy <: JdiProxy: ClassTag, Underlying <: ObjectReference](from: JdiProxy)(apply: (JdiContext, Underlying) => Proxy): Proxy = from match {
    case np: NullJdiProxy => apply(np.proxyContext, np.__underlying.asInstanceOf[Underlying])
    case wrapper: JdiProxyWrapper => unwrap(wrapper.__outer)(apply)
    case proxy: Proxy => proxy
    case _ =>
      val className = classTag[Proxy].runtimeClass.getSimpleName
      throw new IllegalArgumentException(s"Cannot create proxy of type: '$className' from: '$from'")
  }
}

/**
 * Simplest implementation of [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
case class SimpleJdiProxy(proxyContext: JdiContext, __underlying: ObjectReference) extends JdiProxy

/**
 * Implementation of wrapper on JdiProxies. Stubs generated during reflective compilation extends from it.
 *
 * WARNING - this class is used in reflective compilation.
 * If you change it's name, package or behavior, make sure to change it also.
 */
class JdiProxyWrapper(protected[expression] val __outer: JdiProxy) extends JdiProxy {
  override protected[expression] val proxyContext: JdiContext = __outer.proxyContext
  override protected[expression] def __underlying: ObjectReference = __outer.__underlying

  /** Implementation of method application. */
  override def applyDynamic(name: String)(args: Any*): JdiProxy = __outer.applyDynamic(name)(args: _*)

  /** Implementation of field selection. */
  override def selectDynamic(name: String): JdiProxy = __outer.selectDynamic(name)

  /** Implementation of variable mutation. */
  override def updateDynamic(name: String)(value: Any): Unit = __outer.updateDynamic(name)(value)

  /** Forwards equality to debugged jvm */
  override def ==(other: JdiProxy): BooleanJdiProxy = __outer.==(other)
}

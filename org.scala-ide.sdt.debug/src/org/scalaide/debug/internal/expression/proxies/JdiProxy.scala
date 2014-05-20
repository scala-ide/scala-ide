/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy

import com.sun.jdi.ObjectReference

import scala.language.dynamics

/**
 * Base for all proxies using JDI. Extends `scala.Dynamic` to proxy all method calls to debugged jvm using
 * [[org.scalaide.debug.internal.expression.proxies.JdiContext]].
 */
trait JdiProxy extends Dynamic {

  /**
   * Context in which debug is running.
   * See [[org.scalaide.debug.internal.expression.proxies.JdiContext]] for more information.
   */
  protected[expression] def context: JdiContext

  /** Reference that lies under this proxy. */
  protected[expression] def underlying: ObjectReference

  /** Type of underlying reference. */
  protected[expression] def objectType = underlying.referenceType

  /** Some if proxy is have generic this type other then jdi type
  * iI applies mostly to primitives that are of Rich* class and should be boxed for some methods */
  protected[expression] def genericThisType: Option[String] = None

  /** Implementation of method application. */
  def applyDynamic(name: String)(args: Any*): JdiProxy =
    context.invokeMethod[JdiProxy](this, genericThisType, name, Seq(args.map(_.asInstanceOf[JdiProxy])))

  /** Implementation of field selection. */
  def selectDynamic(name: String): JdiProxy =
    context.invokeMethod[JdiProxy](this, genericThisType, name, Seq())

  /** Implementation of variable mutation. */
  def updateDynamic(name: String)(value: Any): Unit =
    context.invokeMethod[JdiProxy](this, genericThisType, s"${name}_=", Seq(Seq(value.asInstanceOf[JdiProxy])))

  /** Forwards equality to debugged jvm */
  def ==(other: JdiProxy): BooleanJdiProxy =
    context.invokeMethod[BooleanJdiProxy](this, genericThisType, "equals", Seq(Seq(other)))

  /** Forwards inequality to debugged jvm */
  def !=(other: JdiProxy): BooleanJdiProxy =
    !(this == other)
}

/**
 * Base for companion objects for [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
trait JdiProxyCompanion[Proxy <: JdiProxy] {

  /** Creates a JdiProxy for object using context */
  def apply(proxyContext: JdiContext, underlying: ObjectReference): Proxy

  /** Creates a JdiProxy based on existing one */
  def apply(on: JdiProxy): Proxy =
    on match {
      case wrapper: JdiProxyWrapper => apply(wrapper.outer)
      case boxed: (Proxy@unchecked) => boxed
      case _ => throw new RuntimeException("Proxy is not supported: " + on)
    }
}

/**
 * Simplest implementation of [[org.scalaide.debug.internal.expression.proxies.JdiProxy]].
 */
case class SimpleJdiProxy(context: JdiContext, underlying: ObjectReference) extends JdiProxy

/**
 * Implementation of wrapper on JdiProxies. Stubs genereted during reflective compilation extends from it.
 *
 * WARNING - this class is used in reflective compilation.
 * If you change it's name, package or behavior, make sure to change it also.
 */
class JdiProxyWrapper(protected[expression] val outer: JdiProxy) extends JdiProxy {
  override protected[expression] val context: JdiContext = outer.context
  override protected[expression] val underlying: ObjectReference = outer.underlying
}

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
   * Method for calling special methods (that are not simply translated to the method invocation) like update on arrays
   * @return Some(result) if special method is called, None otherwise
   */
  protected def callSpecialMethod(name: String, args: Seq[Any]): Option[JdiProxy] = None

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
    callSpecialMethod(name, args).getOrElse {
      proxyContext.invokeMethod(this, genericThisType, name, args.map(_.asInstanceOf[JdiProxy]))
    }

  /** Implementation of field selection. */
  def selectDynamic(name: String): JdiProxy =
    callSpecialMethod(name, Seq()).getOrElse {
      proxyContext.invokeMethod(this, genericThisType, name, Seq())
    }

  /** Implementation of variable mutation. */
  def updateDynamic(name: String)(value: Any): Unit =
    proxyContext.invokeMethod(this, genericThisType, s"${name}_=", Seq(value.asInstanceOf[JdiProxy]))

  /** Forwards equality to debugged jvm */
  def ==(other: JdiProxy): JdiProxy =
    proxyContext.invokeMethod(this, genericThisType, "equals", Seq(other))

  /** Forwards inequality to debugged jvm */
  def !=(other: JdiProxy): JdiProxy =
    proxyContext.proxy(!(this == other).__value[Boolean])

  //TODO O-7468 Add proper support for implicit conversion from Predef
  def ->(a: JdiProxy): JdiProxy = {
    val wrapped = proxyContext.newInstance("scala.Predef$ArrowAssoc", Seq(this))
    proxyContext.invokeMethod(wrapped, None, "->", Seq(a))
  }


  /**
   *  Method added to override standard "+" method that is defied for all objects and takes String as argument
   *  (compiler does not convert '+' to apply dynamic - it only complains about argument that is JdiProxy not String)
   */
  def +(v: JdiProxy): JdiProxy =
    proxyContext.invokeMethod(this, genericThisType, "+", Seq(v))

  /** Obtain primitive value from proxy of given type - implemented only in primitives */
  def __value[I]: I = throw new UnsupportedOperationException(s"$this is not a primitive.")
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
  private[proxies] def unwrap[Proxy <: JdiProxy : ClassTag, Underlying <: ObjectReference](from: JdiProxy)(apply: (JdiContext, Underlying) => Proxy): Proxy = from match {
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
  override def ==(other: JdiProxy): JdiProxy = __outer.==(other)
}

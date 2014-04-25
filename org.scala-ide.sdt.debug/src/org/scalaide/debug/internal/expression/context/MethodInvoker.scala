/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Value

trait MethodInvoker extends Any {

  /**
   * Invokes a method on a proxy.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   *
   * @param proxy
   * @param methodName
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
   * @return JdiProxy with a result of a method call
   */
  def invokeMethod[Result <: JdiProxy](proxy: JdiProxy, methodName: String, args: Seq[Seq[JdiProxy]] = Seq.empty, implicits: Seq[JdiProxy] = Seq.empty): Result

  /**
   * Invokes a method on a proxy. Returns unboxed value.
   *
   * @param proxy
   * @param methodName
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
   * @return jdi unboxed Value with a result of a method call
   */
  def invokeUnboxed[Result <: Value](proxy: JdiProxy, methodName: String, args: Seq[Seq[JdiProxy]], implicits: Seq[JdiProxy] = Seq.empty): Result

  /**
   * Creates new instance of given class.
   *
   * @param className class for object to create
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
   */
  def newInstance(className: String, args: Seq[Seq[JdiProxy]], implicits: Seq[JdiProxy] = Seq.empty): JdiProxy
}
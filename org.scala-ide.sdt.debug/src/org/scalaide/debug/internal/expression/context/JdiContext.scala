/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import org.scalaide.debug.internal.expression.DebuggerSpecific

import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine

/**
 * Companion for JdiContext, contains names to be used in reflective compilation.
 */
object JdiContext {

  /**
   * Method to mark that given val/def that needs to be implemented in later phases.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def placeholder = ???

  /**
   * Methods to mark that given lambda  that needs to be implemented in later phases.
   *
   * @param lambdaName name of proxy for this lambda - use only to pass information in AST
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def placeholderPartialFunction[Ret](lambdaName: String): PartialFunction[Any, Ret] = ???

  /**
   * Methods to mark that given lambda  that needs to be implemented in later phases.
   *
   * @param lambdaName name of proxy for this lambda - use only to pass information in AST
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  def placeholderFunction1[Ret](lambdaName: String): Any => Ret = _ => ???

  /** see `placeholderFunction1` */
  def placeholderFunction2[Ret](lambdaName: String): (Any, Any) => Ret = (_, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction3[Ret](lambdaName: String): (Any, Any, Any) => Ret = (_, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction4[Ret](lambdaName: String): (Any, Any, Any, Any) => Ret = (_, _, _, _) => ???

  /**
   * Prefixes class name with object prefix.
   * Used internally in generated stubs.
   */
  def toObject(className: String): String = DebuggerSpecific.objNamePrefix + className

  def apply(currentThread: ThreadReference, expressionClassLoader: ClassLoader): JdiContext =
    new JdiContext(currentThread, expressionClassLoader)

}

/**
 * Represents context of debug.
 *
 * Allows for mirroring values and creaties proxies for them as well as invoking methods in context of debugged JVM.
 *
 * @param currentThread Current thread in debug
 */
class JdiContext protected (
  protected val currentThread: ThreadReference,
  protected val expressionClassLoader: ClassLoader)
  extends JdiMethodInvoker
  with JdiVariableContext
  with JdiClassLoader
  with Seeker
  with Proxyfier
  with Stringifier {

  /** JVM underlying current thread. */
  protected def jvm: VirtualMachine = currentThread.virtualMachine()

  protected def topFrame = currentThread.frame(0)
}

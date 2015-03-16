/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import org.scalaide.debug.internal.expression.DebugState
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.context.extensions.ExtendedContext

import com.sun.jdi.StackFrame
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
   * If you change its name, package or behavior, make sure to change it also.
   */
  def placeholder = ???

  /**
   * Method to mark that given nested function needs to be implemented in later phases.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def placeholderNestedMethod(parametersListsCount: Int, beginLine: Int, endLine: Int) = ???

  /**
   * Methods to mark that given lambda  that needs to be implemented in later phases.
   *
   * @param lambdaName name of proxy for this lambda - use only to pass information in AST
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def placeholderPartialFunction[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): PartialFunction[Any, Ret] = ???

  /**
   * Used to obtain types for given values in scope.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def placeholderArgs(args: Any*) = ???

  /**
   * Methods to mark that given lambda  that needs to be implemented in later phases.
   *
   * @param lambdaName name of proxy for this lambda - use only to pass information in AST
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  def placeholderFunction1[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): Any => Ret = _ => ???

  /** see `placeholderFunction1` */
  def placeholderFunction2[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any) => Ret = (_, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction3[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any) => Ret = (_, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction4[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any) => Ret = (_, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction5[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction6[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction7[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction8[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction9[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction10[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction11[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction12[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction13[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction14[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction15[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction16[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction17[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction18[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction19[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction20[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction21[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  /** see `placeholderFunction1` */
  def placeholderFunction22[Ret](lambdaName: String, closureParams: Seq[Any] = Nil): (Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any, Any) => Ret = (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => ???

  def apply(expressionClassLoader: ClassLoader, debugState: DebugState): JdiContext =
    new JdiContext(expressionClassLoader, debugState)

}

/**
 * Represents context of debug.
 *
 * Allows for mirroring values and creates proxies for them as well as invoking methods in context of debugged JVM.
 *
 * @param currentThread Current thread in debug
 * @param debugState provides state of debug - current frame and thread
 */
class JdiContext protected (protected val expressionClassLoader: ClassLoader, debugState: DebugState)
  extends JdiMethodInvoker
  with JdiVariableContext
  with JdiClassLoader
  with Seeker
  with Proxyfier
  with Stringifier
  with InstanceOf
  with HashCode {

  /** JVM underlying current thread. */
  protected def jvm: VirtualMachine = currentThread().virtualMachine()

  protected[expression] def currentThread(): ThreadReference = debugState.currentThread()

  protected def currentFrame(): StackFrame = debugState.currentFrame()

  override def toString: String = s"JdiContext(thread = ${currentThread().name})"

  //current transformation context
  protected final val transformationContext = ExtendedContext(currentFrame())
}

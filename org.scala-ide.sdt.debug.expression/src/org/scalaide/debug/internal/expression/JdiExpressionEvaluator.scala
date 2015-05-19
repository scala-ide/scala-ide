/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import java.io.File

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.util.Failure
import scala.util.Try

import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.logging.HasLogger

import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference

/**
 * Provides state of debug - current frame and thread.
 */
trait DebugState {
  def currentThread(): ThreadReference

  def currentFrame(): StackFrame
}

/**
 * Default implementation of current debug state, based on mutable global state.
 *
 * This could be used when debug is suspended at breakpoint, which means it CANNOT be used in conditional breakpoints -
 * it's checked before breakpoint is hit.
 */
object GlobalDebugState extends DebugState {
  override def currentThread(): ThreadReference = ExpressionManager.currentThread().map(_.threadRef)
    .getOrElse(throw new IllegalStateException("There is no thread!"))

  override def currentFrame(): StackFrame = ScalaDebugger.currentFrame()
    .getOrElse(throw new IllegalStateException("There is no frame!"))
}

/**
 * Expression evaluator implementation based on JDI.
 *
 * @param classpath for project that is debugged
 * @param monitor for reporting progress
 * @param debugState how to obtain current thread and frame. By default, [[org.scalaide.debug.internal.expression.GlobalDebugState]] is used.
 */
class JdiExpressionEvaluator(
  classPath: Option[Seq[String]],
  monitor: ProgressMonitor = NullProgressMonitor,
  debugState: DebugState = GlobalDebugState)
    extends ExpressionEvaluator(JdiExpressionEvaluator.classloaderForRun(classPath), monitor) {

  /** Applies this evaluator to given expression */
  final def apply(expression: String): Try[JdiProxy] = {
    logger.info(s"Compiling:\n\t$expression")
    val context = createContext()
    for {
      compiledExpression <- compileExpression(context)(expression)
      // all errors are required
      res <- scala.util.control.Exception.allCatch.withTry {
        monitor.startNamedSubTask("Executing code")
        val result = compiledExpression.apply(context)
        monitor.reportProgress(1)
        result
      } recoverWith {
        case t: Throwable =>
          // log tree which caused exception during evaluation
          monitor.done()
          logger.debug(s"Exception thrown from evaluated code. Compiled tree:\n${compiledExpression.code}")
          Failure(t)
      }
    } yield res
  }

  /**
   * Create new context from this evaluator
   */
  final def createContext() = JdiContext(projectClassLoader, debugState, classPath.isDefined)
}

protected object JdiExpressionEvaluator extends HasLogger {

  /** Helper method for JdiExpressionEvaluator constructor */
  def classloaderForRun(classPath: Option[Seq[String]]): ClassLoader = {
    val classpathEntries = classPath.getOrElse(Nil)
    // TODO - O-4868 - add window for classpath selection
    val urls = classpathEntries.map(name => new File(name).toURI.toURL)
    logger.debug("Classpath: " + urls.mkString(", "))
    new URLClassLoader(urls, classOf[JdiContext].getClassLoader)
  }
}
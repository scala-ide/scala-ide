/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

import scala.tools.nsc.util.ScalaClassLoader.URLClassLoader
import scala.util.Failure
import scala.util.Try

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.model.ScalaDebugTarget

import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.InvocationException
import com.sun.jdi.ThreadReference

/**
 * Expression evaluator implementation based on JDI.
 *
 * @param debugTarget
 * @param thread
 */
class JdiExpressionEvaluator(debugTarget: ScalaDebugTarget, thread: ThreadReference)
  extends ExpressionEvaluator(JdiExpressionEvaluator.classloaderForRun(debugTarget)) {

  /** Applies this evaluator to given expression */
  final def apply(expression: String): Try[JdiProxy] = {
    logger.info(s"Compiling:\n$expression")
    val context = createContext()
    for {
      compiledExpression <- compileExpression(context)(expression)
      res <- Try(compiledExpression.apply(context)).recoverWith {
        case ie: InvocationException =>
          val underlying = context.valueProxy(ie.exception)
          Failure(new MethodInvocationException(context.show(underlying, withType = false), ie))
        case nothingInferred: NothingTypeInferred =>
          Failure(nothingInferred)
        case jdiProxyFunctionParameter: JdiProxyFunctionParameter =>
          Failure(jdiProxyFunctionParameter)
        case cnl: ClassNotLoadedException =>
          logger.error(s"Class with name: ${cnl.className} was not loaded.")
          handleUnknownException(cnl)
        case e: Throwable =>
          handleUnknownException(e)
      }
    } yield res
  }

  private def handleUnknownException(e: Throwable) = {
    val stackTraceString = {
      val sw = new StringWriter()
      e.printStackTrace(new PrintWriter(sw))
      sw.toString
    }
    val message = s"""Exception was thrown during expression evaluation.
                     |If you see this message, evaluated code probably uses some not-yet-supported Scala feature.
                     |If you want this fixed you are welcome to fill a bug report on Scala IDE tracker.
                     |Exception details:
                     |${e.getMessage}
                     |$stackTraceString""".stripMargin
    Failure(new RuntimeException(message, e))
  }

  /**
   * Create new context from this evaluator
   */
  final def createContext() = JdiContext(thread, classLoader)
}

protected object JdiExpressionEvaluator {

  /** Helper method for JdiExpressionEvaluator constructor */
  def classloaderForRun(debugTarget: ScalaDebugTarget): ClassLoader = {
    // TODO - O-4868 - add window for classpath selection
    val classPath = debugTarget.classPath.getOrElse(Nil)
    val urls = classPath.map(name => new File(name).toURI.toURL)
    new URLClassLoader(urls, classOf[JdiContext].getClassLoader)
  }

}
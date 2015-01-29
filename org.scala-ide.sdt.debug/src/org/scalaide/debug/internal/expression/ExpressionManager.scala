/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.tools.reflect.ToolBoxError
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaThread
import org.scalaide.debug.internal.model.ScalaValue
import org.scalaide.logging.HasLogger

import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.InvocationException
import com.sun.jdi.Location
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent

/**
 * Simple progress indicator not to make expression evaluator depend on eclipse classes.
 */
trait ProgressMonitor {

  /** Reports some amount of work */
  def reportProgress(amount: Int): Unit

  /** Starts named sub-task, name is displayed on UI */
  def startNamedSubTask(name: String): Unit
}

/**
 * Implementation of ProgressMonitor that does nothing.
 */
object NullProgressMonitor extends ProgressMonitor {
  def reportProgress(amount: Int): Unit = ()
  def startNamedSubTask(name: String): Unit = ()
}

/**
 * Main entry point to expression evaluation.
 *
 * It's initialized in `ScalaDebugger.init()` method and registers itself as debug event listener.
 */
object ExpressionManager extends ExpressionManager

/**
 * Interface of Expression manager based on evaluator.
 */
trait ExpressionManager extends HasLogger {

  /**
   * Thread suspended on breakpoint.
   */
  private[expression] def currentThread(): Option[ScalaThread] =
    Option(ScalaDebugger.currentThread)

  /** For progress indication */
  val numberOfPhases = 24

  /** Monitor used to check if there is any expression evaluation in progress */
  object EvaluationStatus {
    @volatile var isInProgress = false

    /**
     * Monitor evaluation of expression
     * @param block code used for evaluation
     */
    def monitor[T](block: => T): T = {
      try {
        isInProgress = true
        block
      } finally {
        isInProgress = false
      }
    }
  }

  /**
   * Computes an expression.
   *
   * @param exp expression to evaluate
   * @param monitor progress monitor for user interface
   * @return see [[org.scalaide.debug.internal.expression.ExpressionEvaluatorResult]]
   */
  final def compute(exp: String, monitor: ProgressMonitor = NullProgressMonitor): ExpressionEvaluatorResult = {
    val debugNotRunning = "Expression evaluation works only when debug is running and jvm is suspended"
    val emptyCode = "Expression is empty"

    def show(proxy: JdiProxy): Try[String] = Try(proxy.proxyContext.show(proxy))

    def computeInEvaluator(evaluator: JdiExpressionEvaluator, debugTarget: ScalaDebugTarget): ExpressionEvaluatorResult = {
      val resultWithStringRep = for {
        result <- evaluator.apply(exp)
        outputText <- show(result)
      } yield (result, outputText)

      recoverFromErrors(resultWithStringRep, evaluator.createContext()) match {
        case Success((result, outputText)) =>
          Try(result.__underlying) match {
            case Success(underlying) =>
              SuccessWithValue(ScalaValue(underlying, debugTarget), outputText)
            case Failure(e) =>
              logger.debug(s"Cannot retrieve underlying object for $result", e)
              SuccessWithoutValue(outputText)
          }
        case Failure(exception) =>
          val errorMessage = exception.getMessage
          logger.error(errorMessage, exception)
          EvaluationFailure(errorMessage)
      }
    }

    if (exp.isEmpty) {
      EvaluationFailure(emptyCode)
    } else
      currentThread() match {
        case Some(scalaThread) =>
          EvaluationStatus.monitor {
            val debugTarget = scalaThread.getDebugTarget
            val evaluator = new JdiExpressionEvaluator(debugTarget.classPath, monitor)
            val result = computeInEvaluator(evaluator, debugTarget)

            // it turned out that evaluating an expression makes stack frames invalid what e.g. spoils the variables view
            // that's why it's needed to rebind stack frames in Scala model's stack frames
            scalaThread.refreshStackFrames()

            result
          }
        case None => EvaluationFailure(debugNotRunning)
      }
  }

  /**
   * Checks for given breakpoint if VM should be suspended.
   *
   * @param condition to evaluate
   * @param location of condition in code
   * @param thread on which to evaluate
   * @param classPath of project which is debugged
   * @return Success(should vm be suspended) or Failure(reason why evaluation failed)
   */
  final def shouldSuspendVM(condition: Option[String],
    location: Location,
    thread: ThreadReference,
    classPath: Option[Seq[String]]): Try[Boolean] = condition match {
    case Some(condition) =>
      evaluateCondition(condition, classPath, thread, location)
    case None =>
      Success(!EvaluationStatus.isInProgress)
  }

  private[expression] def evaluateCondition(
    condition: String,
    classPath: Option[Seq[String]],
    thread: ThreadReference,
    location: Location): Try[Boolean] = {

    val conditionDebugState = new DebugState {
      override def currentThread() = thread

      override def currentFrame() = thread.frame(0)
    }

    val evaluator = new JdiExpressionEvaluator(classPath, debugState = conditionDebugState)
    val context = evaluator.createContext()
    val result = new ConditionManager().checkCondition(condition, location)(
      evaluator.compileExpression(context))(_.apply(context))
    recoverFromErrors(result, context)
  }

  object NonexisitngFieldEqualError {

    val exceptionMessagePattern =
      "Note that (.*) extends Any, not AnyRef.\nSuch types can participate in value classes, but instances\ncannot appear in singleton types or in reference comparisons.".r

    def unapply(e: Throwable): Option[String] = {
      e match {
        case assertionError: ToolBoxError =>
          exceptionMessagePattern.findFirstIn(assertionError.getMessage) match {
            case Some(exceptionMessagePattern(name)) => Some(name)
            case _ => None
          }
        case _ => None
      }
    }
  }

  /**
   * Recovers from errors after expression compilation and evaluation.
   * Provides nice, user-readable descriptions for all known problems.
   */
  private def recoverFromErrors[A](result: Try[A], context: JdiContext): Try[A] = result.recoverWith {
    case noInfo: com.sun.jdi.AbsentInformationException =>
      logger.error("Absent information exception", noInfo)
      Failure(NotAtBreakpointException)
    case notAtBreakPoint: com.sun.jdi.IncompatibleThreadStateException =>
      logger.error("Incompatible thread state", notAtBreakPoint)
      Failure(NotAtBreakpointException)
    case ie: InvocationException =>
      logger.error("JDI invocation exception", ie)
      val underlying = context.valueProxy(ie.exception)
      Failure(new MethodInvocationException(context.show(underlying, withType = false), ie))
    case unsupportedFeature: UnsupportedFeature =>
      logger.warn(s"Unsupported feature was used: ${unsupportedFeature.name}", unsupportedFeature)
      Failure(unsupportedFeature)
    case expressionException: ExpressionException =>
      logger.error("Exception during expression evaluation", expressionException)
      Failure(expressionException)
    case NonexisitngFieldEqualError(name) =>
      Failure(MissingField(name))
    case tb: ToolBoxError if tb.getMessage.contains("not found: type") =>
      Failure(new ReflectiveCompilationFailedWithClassNotFound(tb.getMessage))
    case tb: ToolBoxError =>
      Failure(new ReflectiveCompilationFailure(tb.getMessage))
    case cnl: ClassNotLoadedException =>
      logger.error(s"Class with name: ${cnl.className} was not loaded.", cnl)
      handleUnknownException(cnl)
    case e: Throwable =>
      logger.error("Unknown exception during evaluation", e)
      handleUnknownException(e)
  }

  /** Handles unknown exceptions with nice message for users */
  private def handleUnknownException(e: Throwable) = {
    val currentMessage =
      if (e.getMessage() != null) s"\nException message: ${e.getMessage()}"
      else ""
    val newMessage = s"Exception was thrown during expression evaluation. To see more details check scala-ide error log.$currentMessage"
    Failure(new RuntimeException(newMessage, e))
  }
}

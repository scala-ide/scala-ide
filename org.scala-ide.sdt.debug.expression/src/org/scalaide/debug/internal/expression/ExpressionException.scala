/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.tools.reflect.ToolBoxError
import scala.util.Failure
import scala.util.Try
import scala.util.control.NoStackTrace

import org.scalaide.debug.internal.expression.ExpressionManager.NonexisitngFieldEqualError
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.logging.Logger

import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.InvocationException

/**
 * Marker for all known exceptions from expression evaluator.
 * Stack traces are suppresed for those exceptions.
 */
sealed trait ExpressionException extends Throwable with NoStackTrace

/** Long messages are placed here for readability */
object ExpressionException {

  val throwDetected = "Throwing exception from evaluated code is not possible."

  val functionProxyArgumentTypeNotInferredMessage =
    """You used a lambda expression for which we could not infer its type parameters.
      |Please provide explicit types for all lambda parameters.""".stripMargin

  val lambdaNestedInLambdaMessage = "Lambda containing another lambdas is currently not supported."

  def methodInvocationMessage(reason: String) = s"""Exception was thrown from debugged code, message: "$reason"."""

  def noBooleanJdiProxyExceptionMessage(resultType: String) = s"Result type of expression must be boolean, was $resultType"

  def reflectiveCompilationFailureMessage(reason: String) =
    "Compilation failed\n" + reason.replaceFirst("""reflective typecheck has failed\: """, "")

  def reflectiveCompilationFailureMessageWithDefaultPackageHint(reason: String): String =
    reflectiveCompilationFailureMessage(reason) +
      """
        |If you are sure this class exist make sure you don't use default (empty) packages, as those are not supported.""".stripMargin

  val notAtBreakpointExceptionMessage =
    s"""Current thread is not suspended as a result of JDI event.
       |Evaluating expressions in such state is not possible due to JDI limitation.""".stripMargin

  def unsupportedFeatureMessage(featureName: String) =
    s"""Using '$featureName' is not currently supported by expression evaluator."""

  def notExistingField(name: String) =
    s"Field $name does not exist in current scope."

  def lambdaCompilationFailure(lambdaSource: String): String =
    s"Could not compile lambda expression: \n$lambdaSource\nSee underlying message for more details."

  def multipleMethodsMatchNestedOneMessage(methodName: String, candidates: Seq[String]) =
    s"""We cannot determine real name of nested method: $methodName}.
       |Candidates: ${candidates.mkString(", ")}
       |Nested methods are not fully supported in expression evaluator.")""".stripMargin

  /**
   * Recovers from errors after expression compilation and evaluation.
   * Provides nice, user-readable descriptions for all known problems.
   */
  private[expression] def recoverFromErrors[A](result: Try[A], context: JdiContext, logger: Logger): Try[A] = result.recoverWith {
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
    case lambdaCompilationException: LambdaCompilationFailure =>
      logger.warn(lambdaCompilationException.getMessage, lambdaCompilationException.getCause)
      Failure(lambdaCompilationException)
    // WARNING - this case catches ALL ExpressionExceptions, do not add it's subclasses below it
    case expressionException: ExpressionException =>
      logger.error("Exception during expression evaluation", expressionException)
      Failure(expressionException)
    case NonexisitngFieldEqualError(name) =>
      Failure(MissingField(name))
    case tb: ToolBoxError if tb.getMessage.contains("not found: type") =>
      Failure(new ReflectiveCompilationFailedWithClassNotFound(tb.getMessage))
    case tb: ToolBoxError =>
      Failure(new ReflectiveCompilationFailure(tb.getMessage))
    case nsme: NoSuchMethodError =>
      Failure(new RuntimeException(nsme.getMessage, nsme))
    case cnl: ClassNotLoadedException =>
      logger.error(s"Class with name: ${cnl.className} was not loaded.", cnl)
      handleUnknownException(cnl)
    case e: Throwable =>
      logger.error("Unknown exception during evaluation", e)
      handleUnknownException(e)
  }

  /** Handles unknown exceptions with nice message for users */
  private def handleUnknownException(e: Throwable) = {
    val currentMessage = s"Exception message: ${e.getMessage}"
    val newMessage = s"Exception was thrown during expression evaluation. To see more details check scala-ide error log.\n$currentMessage"
    Failure(new RuntimeException(newMessage, e))
  }

}

/**
 * Raised when user uses `throw` inside expression which makes little sense.
 */
class ThrowDetected
  extends RuntimeException(ExpressionException.throwDetected)
  with ExpressionException

class LambdaCompilationFailure(lambdaSource: String, reason: Throwable)
  extends RuntimeException(ExpressionException.lambdaCompilationFailure(lambdaSource), reason)
  with ExpressionException

case class JavaStaticInvocationProblem(description: String)
  extends RuntimeException(description)
  with ExpressionException

case class MissingField(name: String)
  extends RuntimeException(ExpressionException.notExistingField(name))
  with ExpressionException

object NotAtBreakpointException
  extends RuntimeException(ExpressionException.notAtBreakpointExceptionMessage)
  with ExpressionException

/**
 * Raised when compilation in toolbox fails with class not found exception - we show hint
 * then about default package.
 */
class ReflectiveCompilationFailedWithClassNotFound(reason: String)
  extends RuntimeException(ExpressionException.reflectiveCompilationFailureMessageWithDefaultPackageHint(reason))
  with ExpressionException

/**
 * Raised when compilation in toolbox fails.
 */
class ReflectiveCompilationFailure(reason: String)
  extends RuntimeException(ExpressionException.reflectiveCompilationFailureMessage(reason))
  with ExpressionException

/**
 * Raised when result of condition is not a proxy for boolean value.
 */
class NoBooleanJdiProxyException(resultType: String)
  extends RuntimeException(ExpressionException.noBooleanJdiProxyExceptionMessage(resultType))
  with ExpressionException

/**
 * Raised when code run in debug context throws an exception.
 */
class MethodInvocationException(reason: String, underlying: Throwable)
  extends RuntimeException(ExpressionException.methodInvocationMessage(reason), underlying)
  with ExpressionException

/**
 * Raised when not all types of arguments to lambda was inferred.
 */
object FunctionProxyArgumentTypeNotInferredException
  extends RuntimeException(ExpressionException.functionProxyArgumentTypeNotInferredMessage)
  with ExpressionException

/**
 * Raised when you nest lambda inside another lambda, which is currently not supported.
 */
object NestedLambdaException
  extends RuntimeException(ExpressionException.lambdaNestedInLambdaMessage)
  with ExpressionException

/**
 * Raised when user uses not supported feature.
 */
class UnsupportedFeature(val name: String)
  extends RuntimeException(ExpressionException.unsupportedFeatureMessage(name))
  with ExpressionException

/**
 * Raised when we cannot determine which method match nested one
 */
class MultipleMethodsMatchNestedOne(methodName: String, candidates: Seq[String])
  extends RuntimeException(ExpressionException.multipleMethodsMatchNestedOneMessage(methodName, candidates))
  with ExpressionException

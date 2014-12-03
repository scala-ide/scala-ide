/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.util.control.NoStackTrace

/**
 * Marker for all known exceptions from expression evaluator.
 * Stack traces are suppresed for those exceptions.
 */
sealed trait ExpressionException extends Throwable with NoStackTrace

/** Long messages are placed here for readability */
object ExpressionException {

  val nothingTypeInferredMessage =
    "scala.Nothing was inferred as a result of an expression, which makes no sense and thus is not supported."

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

}

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
 * Raised when `Nothing` is inferred as return type from expression.
 *
 * Evaluating Nothing-returning methods in evaluator is pretty useless, or even harmful if they recurse indefinitely,
 * and will require extra work to support.
 */
object NothingTypeInferredException
  extends RuntimeException(ExpressionException.nothingTypeInferredMessage)
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


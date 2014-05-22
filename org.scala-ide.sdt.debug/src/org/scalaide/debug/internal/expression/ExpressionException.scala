/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

/**
 * Marker for all known exceptions from expression evaluator.
 */
sealed trait ExpressionException { self: Throwable => }

/** Long messages are placed here for readability */
object ExpressionException {

  val nothingTypeInferredMessage =
    "scala.Nothing was inferred as a result of an expression, which makes no sense and thus is not supported."

  val jdiProxyFunctionParameterMessage =
    """Provided lambda has not inferred type of argument.
      |It is typically cause by lack of generic in JVM.
      |Provide valid type arguments to make it work.""".stripMargin

  val cannotCompileLambdaMessage =
    """One of lambdas used in the code could not be compiled.
      |This may be caused by closing over some value, which is not supported,
      |or some problems with generic types and erasure.""".stripMargin
}

/**
 * Raised when code run in debug context throws an exception.
 */
class MethodInvocationException(reason: String, underlying: Throwable)
  extends RuntimeException(s"""Exception was thrown from debugged code, message: "$reason".""", underlying)
  with ExpressionException

class NothingTypeInferred
  extends RuntimeException(ExpressionException.nothingTypeInferredMessage)
  with ExpressionException

class JdiProxyFunctionParameter
  extends RuntimeException(ExpressionException.jdiProxyFunctionParameterMessage)
  with ExpressionException

class CannotCompileLambda(underlying: Throwable)
  extends RuntimeException(ExpressionException.cannotCompileLambdaMessage, underlying)
  with ExpressionException
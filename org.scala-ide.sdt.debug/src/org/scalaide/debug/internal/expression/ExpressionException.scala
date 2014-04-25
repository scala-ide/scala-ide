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

/**
 * Raised when code run in debug context throws an exception.
 */
class MethodInvocationException(reason: String)
  extends RuntimeException(s"""Exception was thrown from debugged code, message: "$reason".""")
  with ExpressionException

class NothingTypeInferred
  extends RuntimeException("scala.Nothing was inferred as a result of an expression, which makes no sense and thus is not supported.")
  with ExpressionException

class JdiProxyFunctionParameter
  extends RuntimeException("Provided lambda has not inferred type of argument. It is typically cause by lack of generic in JVM. Provide valid type for type arguments to make it work.")
  with ExpressionException


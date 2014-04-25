/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import org.junit.Assert._
import org.junit.Test
import org.scalaide.logging.HasLogger

import com.sun.jdi.ThreadReference

class ExpressionManagerTest extends BaseIntegrationTest(ExpressionManagerTest) {

  /**
   * Executes code using [[org.scalaide.debug.internal.expression.ExpressionManager]] and checks result.
   *
   * @param code to compile and run
   * @param expectedResult `Some(<string that should be returned>)` or `None` if error should be returned
   * @param expectedError `Some(<string that should exist in result>)` or `None` if correct result should be returned
   */
  protected final def withExpressionManager(code: String, expectedResult: Option[String], expectedError: Option[String]) = {
    object ExpressionManager extends ExpressionManager with HasLogger {
      protected override def currentEvaluator: Option[JdiExpressionEvaluator] = Some(companion.expressionEvaluator)

      protected override def currentSession: Option[DebuggingSession] = None

      protected def createEvaluator(currentSession: DebuggingSession, thread: ThreadReference): JdiExpressionEvaluator =
        companion.expressionEvaluator
    }

    var result: Option[String] = None

    var error: String = null

    ExpressionManager.compute(code, s => result = Some(s), s => error = s)

    expectedError.foreach(expected => assertTrue(s"'$error' does not contain '$expected'", error.contains(expected)))
    assertEquals(expectedResult, result)
  }

  @Test
  def testDisplayIntResult(): Unit = withExpressionManager(
    code = "int",
    expectedError = None,
    expectedResult = Some(s"${TestValues.Values.int} (of type: java.lang.Integer)"))

  @Test
  def testDisplayEmptyExpressionError(): Unit = withExpressionManager(
    code = "",
    expectedError = Some("<<< Expression is empty >>>"),
    expectedResult = None)

  @Test
  def testDisplayInvalidExpressionError(): Unit = withExpressionManager(
    code = "1 === 2",
    expectedError = Some("<<< Expression evaluation failed >>>"),
    expectedResult = None)

  @Test
  def testDisplayInvalidExpressionErrorWithTypeIssue(): Unit = withExpressionManager(
    code = "List.alaString",
    expectedError = Some("<<< Expression evaluation failed >>>"),
    expectedResult = None)

  @Test
  def testDisplayExceptionMessage(): Unit = withExpressionManager(
    code = "Seq(1, 2, 3).apply(4)",
    expectedError = Some("java.lang.IndexOutOfBoundsException: 4"),
    expectedResult = None)

  @Test
  def testDisplayIllegalNothingTypeInferred(): Unit = withExpressionManager(
    code = "None.get",
    expectedError = Some("Nothing was inferred"),
    expectedResult = None)

  @Test
  def `lambda without inferred type`(): Unit =
    withExpressionManager("list.map(_ - 1)", None, Some("Provided lambda has not inferred type of argument"))

}

object ExpressionManagerTest extends BaseIntegrationTestCompanion
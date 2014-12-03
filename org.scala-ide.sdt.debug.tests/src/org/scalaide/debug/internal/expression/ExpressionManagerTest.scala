/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.util.Failure
import scala.util.Success

import org.junit.Assert._
import org.junit.Test
import org.scalaide.debug.internal.model.ScalaThread
import Names.Scala

class ExpressionManagerTest extends BaseIntegrationTest(ExpressionManagerTest) {

  /**
   * Executes code using [[org.scalaide.debug.internal.expression.ExpressionManager]] and checks result.
   *
   * @param code to compile and run
   * @param expectedResult `Some(<string that should be returned>)` or `None` if error should be returned
   * @param expectedError `Some(<string that should exist in result>)` or `None` if correct result should be returned
   */
  protected final def withExpressionManager(code: String, expectedResult: Option[String], expectedError: Option[String]) = {
    var result: Option[String] = None

    var error: String = null

    ExpressionManager.compute(code) match {
      case SuccessWithValue(scalaValue, outputText) =>
        result = Some(outputText)
      case SuccessWithoutValue(outputText) =>
        result = Some(outputText)
      case EvaluationFailure(errorMessage) =>
        error = errorMessage
    }

    expectedError.foreach(expected => assertTrue(s"'$error' does not contain '$expected'", error.contains(expected)))
    assertEquals(expectedResult, result)
  }

  /**
   * Executes condition using [[org.scalaide.debug.internal.expression.ExpressionManager]] and checks result.
   * Differs from `withExpressionManager` in calling `ExpressionManager.evaluateCondition` instead of `ExpressionManager.compute`.
   *
   * @param code to compile and run
   * @param expectedResult `Some(<boolean that should be returned>)` or `None` if error should be returned
   * @param expectedError `Some(<string that should exist in result>)` or `None` if correct result should be returned
   */
  private def evalConditionWithManager(code: String, expectedResult: Option[Boolean], expectedError: Option[String]): Unit = {
    var result: Option[Boolean] = None

    var error: String = null

    val location = companion.session.currentStackFrame.stackFrame.location
    val debugTarget = companion.session.debugTarget
    val threadRef = companion.session.currentStackFrame.thread.threadRef

    ExpressionManager.evaluateCondition(code, debugTarget, threadRef, location) match {
      case Success(shouldStop) => result = Some(shouldStop)
      case Failure(exception) => error = exception.getMessage
    }

    expectedError.foreach(expected => assertTrue(s"'$error' does not contain '$expected'", error.contains(expected)))
    assertEquals(expectedResult, result)
  }

  @Test
  def testDisplayNullResult(): Unit = withExpressionManager(
    code = "null",
    expectedError = None,
    expectedResult = Some(s"${Scala.nullLiteral} (of type: ${Scala.nullType})"))

  @Test
  def testDisplayNullValue(): Unit = withExpressionManager(
    code = "Libs.nullVal",
    expectedError = None,
    expectedResult = Some(s"${Scala.nullLiteral} (of type: ${Scala.nullType})"))

  // If in this test we'd use function returning Unit, some other tests in this class would fail (they work when we run them separately).
  // In this case there's error from compiler: <Cannot read source file> in scala.tools.nsc.transform.AddInterfaces$LazyImplClassType.implType$1(AddInterfaces.scala:190).
  // And there's no such problem during real work with expression evaluator installed in Eclipse.
  @Test
  def testDisplayUnitResult(): Unit = withExpressionManager(
    code = "print('a')",
    expectedError = None,
    expectedResult = Some(s"${Scala.unitLiteral} (of type: ${Scala.unitType})"))

  @Test
  def testDisplayIntResult(): Unit = withExpressionManager(
    code = "int",
    expectedError = None,
    expectedResult = Some(s"${TestValues.ValuesTestCase.int} (of type: ${Names.Java.boxed.Integer})"))

  @Test
  def testDisplayEmptyExpressionError(): Unit = withExpressionManager(
    code = "",
    expectedError = Some("Expression is empty"),
    expectedResult = None)

  @Test
  def testDisplayInvalidExpressionError(): Unit = withExpressionManager(
    code = "1 === 2",
    expectedError = Some(ExpressionException.reflectiveCompilationFailureMessage("")),
    expectedResult = None)

  @Test
  def testDisplayInvalidConditionError(): Unit = evalConditionWithManager(
    code = "1 === 2",
    expectedError = Some(ExpressionException.reflectiveCompilationFailureMessage("")),
    expectedResult = None)

  @Test
  def testDisplayInvalidExpressionErrorWithTypeIssue(): Unit = withExpressionManager(
    code = "List.alaString",
    expectedError = Some(ExpressionException.reflectiveCompilationFailureMessage("")),
    expectedResult = None)

  @Test
  def testDisplayInvalidConditionErrorWithTypeIssue(): Unit = evalConditionWithManager(
    code = "List.alaString",
    expectedError = Some(ExpressionException.reflectiveCompilationFailureMessage("")),
    expectedResult = None)

  @Test
  def testDisplayExceptionMessage(): Unit = withExpressionManager(
    code = "Seq(1, 2, 3).apply(4)",
    expectedError = Some("java.lang.IndexOutOfBoundsException: 4"),
    expectedResult = None)

  @Test
  def testDisplayIllegalNothingTypeInferred(): Unit = withExpressionManager(
    code = "None.get",
    expectedError = Some(ExpressionException.nothingTypeInferredMessage),
    expectedResult = None)

  @Test
  def testDisplayIllegalNothingTypeInferredInCondition(): Unit = evalConditionWithManager(
    code = "None.get",
    expectedError = Some(ExpressionException.nothingTypeInferredMessage),
    expectedResult = None)

  @Test
  def testDisplayMessageForLambdaWithoutInferredTypeInCondition(): Unit = evalConditionWithManager(
    code = "list.map(_ - 1)",
    expectedError = Some(ExpressionException.noBooleanJdiProxyExceptionMessage("scala.collection.immutable.$colon$colon")),
    expectedResult = None)

  @Test
  def testEqualsOnNonexistingField(): Unit = evalConditionWithManager(
    code = "uuula == 1",
    expectedError = Some(ExpressionException.notExistingField("uuula")),
    expectedResult = None)

}

object ExpressionManagerTest extends BaseIntegrationTestCompanion
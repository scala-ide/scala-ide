/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.runtime.ScalaRunTime
import scala.tools.reflect.ToolBoxError
import scala.util.Failure
import scala.util.Success

import org.eclipse.jdt.debug.core.IJavaBreakpoint
import org.junit.Assert._
import org.junit.BeforeClass
import org.scalaide.debug.internal.expression.TestValues.ValuesTestCase
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.logging.HasLogger

class BaseIntegrationTest(protected val companion: BaseIntegrationTestCompanion) extends HasLogger {

  /** Wraps value with `"` */
  protected def s(a: Any) = '"' + a.toString + '"'

  private object Result {
    private val regex = s"(.*) \\(of type: (.+)\\)".r
    def apply(value: String, tpe: String) = s"$value (of type: $tpe)"
    def unapply(string: String): Option[(String, String)] = string match {
      case regex(value, tpe) => Some((value, tpe))
      case other => None
    }
  }

  /**
   * Test code and returns tuple with (returnedValue, returnedType)
   */
  protected final def runCode(code: String): (String, String) = {
    val proxy = runInEclipse(code, forceRetry = true)
    val resultString = proxy.__context.show(proxy)

    resultString match {
      case Result(resultString, resultType) => (resultString, resultType)
      case resultString =>
        fail(s"'$resultString' don't match 'res (of type: resType)' standard")
        throw new RuntimeException("Fail should throw an exception!")
    }
  }

  /** test code for given value and its type */
  protected final def eval(code: String, expectedValue: Any, expectedType: String): Unit = {
    val (resultValue, resultType) = runCode(code)
    val expectedValueString = ScalaRunTime.stringOf(expectedValue)
    assertEquals("Result value differs:",
      Result(expectedValueString, expectedType),
      Result(resultValue, resultType))
  }

  /**
   * Checks if given error type is thrown i.e. when given operation is not permitted for given type
   */
  protected def expectReflectiveCompilationError(code: String): Unit = {
    try {
      runInEclipse(code, forceRetry = false).toString
      fail(s"ToolBoxError should be thrown")
    } catch {
      case expected: ReflectiveCompilationFailure => // OK
      case other: Throwable =>
        other.printStackTrace()
        fail(s"Expected ReflectiveCompilationFailure, got $other")
    }
  }

  protected def runInEclipse(code: String, forceRetry: Boolean = true): JdiProxy = {
    val eval = companion.expressionEvaluator

    ExpressionException.recoverFromErrors(eval.apply(code), eval.createContext(), logger) match {
      case Success(result) =>
        result
      case Failure(exception) =>
        if (forceRetry) {
          logger.warn("Failed to evaluate expresions. Retrying with new instance of JdiExpressionEvaluator...")
          companion.prepareEvaluator()
          runInEclipse(code, false)
        } else throw exception
    }
  }
}

/**
 * Companion for integration test.
 *
 * @param projectName name of project in `test-workspace` to work on
 * @param fileName name of file to run during test
 * @param lineNumber line number in which breakpoint should be added
 */
class BaseIntegrationTestCompanion(fileName: String, lineNumber: Int)
    extends CommonIntegrationTestCompanion("expression-evaluator") {

  var expressionEvaluator: JdiExpressionEvaluator = null

  def this(testCaseSettings: IntegrationTestCaseSettings = ValuesTestCase) =
    this(testCaseSettings.fileName, testCaseSettings.breakpointLine)

  /** Default suspension policy. Override it to change value for your test. */
  protected def suspensionPolicy: Int = IJavaBreakpoint.SUSPEND_THREAD

  /** Type in which breakpoint is set. By default `debug.<fileName>$`. */
  protected def typeName: String = "debug." + fileName + "$"

  def prepareEvaluator(): Unit = {
    expressionEvaluator = initializeEvaluator(session)
  }

  @BeforeClass
  def prepareTestDebugSession(): Unit = withDebuggingSession(fileName) { newSession =>
    if (!EclipseProjectContext.isRemote)
      refreshBinaryFiles()

    session = newSession

    session.runToLine(typeName, lineNumber, suspendPolicy = suspensionPolicy)

    prepareEvaluator()
  }

}

trait IntegrationTestCaseSettings {
  val fileName: String
  val breakpointLine: Int
}

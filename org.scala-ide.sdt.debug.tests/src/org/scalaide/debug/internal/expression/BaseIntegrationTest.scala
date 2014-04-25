/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import scala.util.Failure
import scala.util.Success

import org.junit.Assert._
import org.junit.BeforeClass
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.logging.HasLogger

import com.sun.jdi.ThreadReference

class BaseIntegrationTest(protected val companion: BaseIntegrationTestCompanion) extends HasLogger {

  private val resultRegex = s"(.+) \\(of type: (.+)\\)".r

  /**
   * Test code and returns tuple with (returnedValue, returnedType)
   */
  protected final def runCode(code: String): (String, String) = {
    val proxy = runInEclipse(code)
    val resultString = proxy.context.show(proxy)

    resultString match {
      case resultRegex(resultString, resultType) => (resultString, resultType)
      case resultString =>
        fail(s"'$resultString' don't match 'res (of type: resType)' standard")
        throw new RuntimeException("Fail should throw an exception!")
    }
  }

  /** test code for given value and its type */
  protected final def eval(code: String, expectedValue: String, expectedType: String): Unit = {
    val (resultValue, resultType) = runCode(code)
    assertEquals("Result value differs:", expectedValue, resultValue)
    assertEquals("Result type differs:", expectedType, resultType)
  }

  /**
   * Checks error thrown i.e. when given operation is not permitted for given type
   */
  protected def evalWithToolboxError(code: String): Unit = {
    try {
      runInEclipse(code).toString
      fail("ToolBoxError should be thrown")
    } catch {
      case e: scala.tools.reflect.ToolBoxError => assertTrue(true)
    }
  }

  private def runInEclipse(code: String): JdiProxy =
    companion.expressionEvaluator.apply(code) match {
      case Success(result) => result
      case Failure(exception) => throw exception
    }
}

class BaseIntegrationTestCompanion(workspace: String = "expression", fileName: String = TestValues.fileName,
  lineNumber: Int = TestValues.breakpointLine) extends CommonIntegrationTestCompanion(workspace) {

  var expressionEvaluator: JdiExpressionEvaluator = null

  @BeforeClass
  def prepareTestDebugSession(): Unit = {
    refreshBinaryFiles()

    session = initDebugSession(fileName)

    val objectName = "debug." + fileName + "$"
    session.runToLine(objectName, lineNumber)

    expressionEvaluator = initializeEvaluator(session)
  }

}

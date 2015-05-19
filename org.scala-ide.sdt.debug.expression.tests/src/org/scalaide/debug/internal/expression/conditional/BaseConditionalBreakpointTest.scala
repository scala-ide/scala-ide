/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.conditional

import scala.util.Failure
import scala.util.Success

import org.junit.Assert._
import org.junit.BeforeClass
import org.scalaide.debug.internal.ConditionContext
import org.scalaide.debug.internal.expression.JdiExpressionEvaluator
import org.scalaide.debug.internal.expression.CommonIntegrationTestCompanion
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.logging.HasLogger

/**
 * Represents expected expression evaluation result at breakpoint
 */
case class ExpectedValueContext(value: String, `type`: String)

class BaseConditionalBreakpointTest(val companion: BaseConditionalBreakpointTestCompanion) extends HasLogger {

  protected def testWithCondition(code: String, conditionContext: ConditionContext,
    expectedValueContext: Option[ExpectedValueContext] = None): Unit = {
    runTest(code, expectedValueContext, Some(conditionContext))
  }

  private def runTest(code: String, expectedValueContext: Option[ExpectedValueContext],
    conditionContext: Option[ConditionContext]): Unit = {
    val resOpt = runInEclipse(code, conditionContext)
    expectedValueContext.map {
      evc =>
        assertTrue(resOpt.isDefined)
        val res = resOpt.get
        val stringValue = res.__context.show(res)
        val expected = s"${evc.value} (of type: ${evc.`type`})"
        assertEquals("Result value differs:", expected, stringValue)
        logger.debug(s"Supported: ${code}\n\n")
    }
  }

  private def runInEclipse(code: String, conditionContext: Option[ConditionContext]): Option[JdiProxy] = {
    val evaluator: Option[JdiExpressionEvaluator] = companion.expressionEvaluator(conditionContext)
    if (companion.shouldHitBreakpoint(conditionContext)) {
      val res = evaluator.get.apply(code) match {
        case Success(result) => result
        case Failure(exception) => throw exception
      }
      Some(res)
    } else {
      None
    }
  }
}

class BaseConditionalBreakpointTestCompanion(workspace: String = "conditional-breakpoints",
  fileName: String = "ConditionalBreakpoints",
  lineNumber: Int = 9) extends CommonIntegrationTestCompanion(workspace) {

  /**
   * Breakpoints is hit when there's no condition or condition should evaluates to true
   */
  final def shouldHitBreakpoint(conditionContext: Option[ConditionContext]): Boolean =
    conditionContext.forall(_.shouldSuspend)

  final def expressionEvaluator(conditionContext: Option[ConditionContext]): Option[JdiExpressionEvaluator] =
    doCreateEvaluatorWithBreakpoint(conditionContext)

  @BeforeClass
  def prepareTestDebugSession() {
    refreshBinaryFiles()
  }

  private def doCreateEvaluatorWithBreakpoint(conditionContext: Option[ConditionContext]): Option[JdiExpressionEvaluator] = withDebuggingSession(fileName) {
    createdSession =>
    session = createdSession

    val objectName = "debug." + fileName + "$"
    session.runToLine(objectName, lineNumber, conditionContext = conditionContext)

    if (shouldHitBreakpoint(conditionContext)) {
      Some(initializeEvaluator(session))
    } else {
      None
    }
  }
}

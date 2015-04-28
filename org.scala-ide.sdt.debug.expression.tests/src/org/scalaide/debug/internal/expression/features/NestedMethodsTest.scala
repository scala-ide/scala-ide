/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.junit.Ignore
import org.junit.Assert._

import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.NestedMethodsTestCase
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.UnsupportedFeature
import org.scalaide.debug.internal.expression.MultipleMethodsMatchNestedOne

class NestedMethodsTest extends BaseIntegrationTest(NestedMethodsTest) {

  private def withUnsuportedFeature(feature: String)(code: String) = {
    try {
      runInEclipse(code, forceRetry = false)
      fail(s"UnsupportedFeature exception about: $feature should be thrown")
    } catch {
      case e: UnsupportedFeature =>
        val message = e.getMessage
        if (!message.contains(feature))
          fail(s"Requested feature: $feature but got $message")
    }
  }

  @Test(expected = classOf[MultipleMethodsMatchNestedOne])
  def testNestedTwice(): Unit = runInEclipse("nestedTwice(1)")

  @Test
  def testNestedInMultipleMethods(): Unit = eval("nestedInMultipleMethods(1)", "nestedInMultipleMethods", Java.String)

  @Test
  def testSimpleNested(): Unit = eval("simpleNested(1)", "simpleNested: 1", Java.String)

  @Test
  def testLocalMethodMiscompilation(): Unit = expectReflectiveCompilationError("nestedStringMethod(1)")

  @Test(expected = classOf[MultipleMethodsMatchNestedOne])
  def testDeclaredAfterBreakpoint(): Unit = runInEclipse("declaredAfterBreakpoint(1)")

  @Test
  def testNestedWithClosure(): Unit = withUnsuportedFeature("closure")("nestedWithClosure(1)")

  @Test
  def testNestedMethodWithoutParenthesis(): Unit = eval("nestedMethodWithoutParenthesis", "nestedMethodWithoutParenthesis", Java.String)

  @Test
  def testNestedDefinedInLambda(): Unit = eval("nestedDefinedInLambda(1)", "nestedDefinedInLambda", Java.String)

  @Test
  def testMultipleParametersNestedMethod(): Unit = eval("multipleParametersNestedMethod(1)(2)", "multipleParametersNestedMethod", Java.String)

  @Test
  def testMultipleParametersNestedMethodReturningFunction(): Unit =
    eval("multipleParametersNestedMethodReturningFunction(1)(2)(3)", "multipleParametersNestedMethodReturningFunction", Java.String)

  @Test
  def testNestedMethodUsedInLambda(): Unit = withUnsuportedFeature("function")("objectList.map(simpleNested)")

  @Test
  def testMultipleParametersNestedMethodUsedInLambda(): Unit = withUnsuportedFeature("function")("objectList.map(multipleParametersNestedMethod(1))")

  @Test
  def testNestedFunction(): Unit = eval("nestedFunction(1)", "nestedFunction", Java.String)

  @Test
  def testNestedWithExistentialType(): Unit = eval("nestedWithExistentialType(Nil)", "nestedWithExistentialType", Java.String)
}

object NestedMethodsTest extends BaseIntegrationTestCompanion(NestedMethodsTestCase)
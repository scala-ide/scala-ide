/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.VariablesTestCase

trait AssignmentTest {
  self: BaseIntegrationTest =>

  protected def testAssignment(on: String, tpe: String, values: String*) = {
    val (oldValue, oldType) = runCode(on)
    try {
      values.foreach { value =>
        val (resultValue, _) = runCode(value)
        runCode(s"$on = $value")
        eval(code = on, expectedValue = resultValue, expectedType = tpe)
      }
    } finally {
      // set old value for next tests
      def haveQuotes(s: String) = s.startsWith("\"") && s.endsWith("\"")
      if (oldType == Java.boxed.String && !haveQuotes(oldValue)) runCode(s"""$on = "$oldValue"""")
      else runCode(s"$on = $oldValue")
    }
  }
}

class VarsTest extends BaseIntegrationTest(VarsTest) with AssignmentTest {

  @Test
  def testVariableAssignment(): Unit =
    testAssignment("state.int", Java.primitives.int, values = "1", "2", "3")

  @Test
  def testLocalVariableAssignment(): Unit =
    testAssignment("localString", Java.boxed.String, values = s("1"), s("2"), s("3"))

  // TODO - O-8559 - This fails with 'InvalidStackFrameException' when you try to assign to local boxed primitive :(
  @Test(expected = classOf[UnsupportedFeature])
  def testLocalVariableAssignmentForBoxedPrimitives(): Unit =
    testAssignment("localBoxedInt", Java.boxed.Integer,
      values = "new java.lang.Integer(1)",
      "new java.lang.Integer(2)",
      "new java.lang.Integer(3)")

  @Test
  def testLocalVariableAssignmentForPrimitives(): Unit =
    testAssignment("localInt", Java.primitives.int, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithImplicitThis(): Unit =
    testAssignment("fieldInt", Java.primitives.int, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithExplicitThis(): Unit =
    testAssignment("this.fieldInt", Java.primitives.int, values = "1", "2", "3")

  @Test
  def testAssignmentWithSameVariableOnLhsAndRhs(): Unit =
    testAssignment("fieldInt", Java.primitives.int, "fieldInt + 1")

  @Test
  def testAssignmentWithTmpVariable(): Unit = {
    runCode("val tmp = fieldInt + 1; fieldInt = tmp")
    eval(code = "fieldInt", expectedValue = "2", expectedType = Java.primitives.int)
  }

  @Test
  def testAssignmentWithVariableOnRhs(): Unit =
    testAssignment("fieldInt", Java.primitives.int, "state.int + 1")

  @Test
  def testStringToStringAssignment(): Unit =
    testAssignment("fieldString", Java.boxed.String, "anotherStringField")

  @Test
  def testSetterMethod(): Unit = {
    runCode(s"state.int_=(1)")
    eval("state.int", 1, Java.primitives.int)
    runCode(s"state.int_=(123)")
    eval("state.int", 123, Java.primitives.int)
  }

  @Test
  def testVariableInExpression(): Unit =
    eval("var a = 1; a = 2; a", 2, Java.primitives.int)
}

object VarsTest extends BaseIntegrationTestCompanion(VariablesTestCase)

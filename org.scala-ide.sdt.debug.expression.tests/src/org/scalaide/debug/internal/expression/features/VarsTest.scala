/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.VariablesTestCase
import org.scalaide.debug.internal.expression.UnsupportedFeature

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
      if (oldType == Java.boxed.String) runCode(s"""$on = "$oldValue"""")
      else runCode(s"$on = $oldValue")
    }
  }
}

class VarsTest extends BaseIntegrationTest(VarsTest) with AssignmentTest {

  @Test
  def testVariableAssignment(): Unit =
    testAssignment("state.int", Java.boxed.Integer, values = "1", "2", "3")

  @Test
  def testLocalVariableAssignment(): Unit = {
    def s(a: Any) = '"' + a.toString + '"'

    testAssignment("localString", Java.boxed.String, values = s("1"), s("2"), s("3"))
    testAssignment("localBoxedInt", Java.boxed.Integer,
      values = "new java.lang.Integer(1)",
      "new java.lang.Integer(2)",
      "new java.lang.Integer(3)")
  }

  // TODO - O-8559 - This fails with 'InvalidStackFrameException' when you try to assign to local primitive :(
  @Test(expected = classOf[UnsupportedFeature])
  def testLocalVariableAssignmentForPrimitives(): Unit =
    testAssignment("localInt", Java.boxed.Integer, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithImplicitThis(): Unit =
    testAssignment("fieldInt", Java.boxed.Integer, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithExplicitThis(): Unit =
    testAssignment("this.fieldInt", Java.boxed.Integer, values = "1", "2", "3")

  @Test
  def testAssignmentWithSameVariableOnLhsAndRhs(): Unit =
    testAssignment("fieldInt", Java.boxed.Integer, "fieldInt + 1")

  @Test
  def testAssignmentWithTmpVariable(): Unit = {
    runCode("val tmp = fieldInt + 1; fieldInt = tmp")
    eval(code = "fieldInt", expectedValue = "2", expectedType = Java.boxed.Integer)
  }

  @Test
  def testAssignmentWithVariableOnRhs(): Unit =
    testAssignment("fieldInt", Java.boxed.Integer, "state.int + 1")

  @Test
  def testStringToStringAssignment(): Unit =
    testAssignment("fieldString", Java.boxed.String, "anotherStringField")

  @Test
  def testSetterMethod(): Unit = {
    runCode(s"state.int_=(1)")
    eval("state.int", "1", Java.boxed.Integer)
    runCode(s"state.int_=(123)")
    eval("state.int", "123", Java.boxed.Integer)
  }

  @Test
  def testVariableInExpression(): Unit =
    eval("var a = 1; a = 2; a", "2", Java.boxed.Integer)
}

object VarsTest extends BaseIntegrationTestCompanion(VariablesTestCase)

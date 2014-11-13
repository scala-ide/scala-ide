/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert._

import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.VariablesTestCase

class VarsTest extends BaseIntegrationTest(VarsTest) {

  private def testAssignment(on: String, tpe: String, values: String*) = values.foreach { value =>
    val (oldValue, oldType) = runCode(on)
    try {
      val (resultValue, _) = runCode(value)
      runCode(s"$on = $value")
      eval(code = on, expectedValue = resultValue, expectedType = tpe)
    } finally {
      // set old value for next tests
      if (oldType == Java.boxed.String) runCode(s"""$on = "$oldValue"""")
      else runCode(s"$on = $oldValue")
    }
  }

  @Test
  def testVariableAssignment(): Unit =
    testAssignment("state.int", Java.boxed.Integer, values = "1", "2", "3")

  @Ignore("TODO - O-5374 - add support for local variables")
  @Test
  def testLocalVariableAssignment(): Unit =
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
  def testLocalVariable(): Unit = eval("var a = 1; a = 2; a", "2", Java.boxed.Integer)

}

object VarsTest extends BaseIntegrationTestCompanion(VariablesTestCase)
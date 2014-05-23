/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert._
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues

class VarsTest extends BaseIntegrationTest(VarsTest) {

  private def testAssignment(on: String, tpe: String, values: String*) = values.foreach { value =>
    runCode(s"$on = $value")
    val (resultValue, _) = runCode(value)
    eval(code = s"$on", expectedValue = resultValue, expectedType = tpe)
  }

  @Test
  def testVariableAssignment(): Unit =
    testAssignment("state.int", JavaBoxed.Integer, values = "1", "2", "3")

  @Ignore("TODO - O-5374 - add support for local variables")
  @Test
  def testLocalVariableAssignment(): Unit =
    testAssignment("localInt", JavaBoxed.Integer, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithImplicitThis(): Unit =
    testAssignment("fieldInt", JavaBoxed.Integer, values = "1", "2", "3")

  @Test
  def testFieldVariableAssignmentWithExplicitThis(): Unit =
    testAssignment("this.fieldInt", JavaBoxed.Integer, values = "1", "2", "3")

  @Ignore("TODO - O-5640 - fix return types on arithmetic operations")
  @Test
  def testAssignmentWithSameVariableOnLhsAndRhs(): Unit =
    testAssignment("fieldInt", JavaBoxed.Integer, "fieldInt + 1")

  @Ignore("TODO - O-5640 - fix return types on arithmetic operations")
  @Test
  def testAssignmentWithTmpVariable(): Unit = {
    runCode("val tmp = fieldInt + 1; fieldInt = tmp")
    eval(code = "fieldInt", expectedValue = "2", expectedType = JavaBoxed.Integer)
  }

  @Ignore("TODO - O-5640 - fix return types on arithmetic operations")
  @Test
  def testAssignmentWithVariableOnRhs(): Unit =
    testAssignment("fieldInt", JavaBoxed.Integer, "state.int + 1")

  @Test
  def testStringToStringAssignment(): Unit =
    testAssignment("fieldString", JavaBoxed.String, "anotherStringField")

  @Test
  def testSetterMethod(): Unit = {
    runCode(s"state.int_=(1)")
    eval("state.int", "1", JavaBoxed.Integer)
    runCode(s"state.int_=(123)")
    eval("state.int", "123", JavaBoxed.Integer)
  }

  @Test
  def testLocalVariable(): Unit = eval("var a = 1; a = 2; a", "2", JavaBoxed.Integer)

}

object VarsTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.variablesFileName,
  lineNumber = TestValues.variablesLineNumber)
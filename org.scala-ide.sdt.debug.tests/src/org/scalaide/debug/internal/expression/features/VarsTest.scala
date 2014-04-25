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

class Foo(var a: Int)

class VarsTest extends BaseIntegrationTest(VarsTest) {

  private def testAssignment(on: String, tpe: String, values: String*) = values.foreach { value =>
    runCode(s"$on = $value")
    eval(s"$on", value, tpe)
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
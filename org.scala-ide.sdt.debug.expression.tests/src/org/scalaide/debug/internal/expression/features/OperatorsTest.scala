/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class OperatorsTest
    extends BaseIntegrationTest(OperatorsTest)
    with AssignmentTest {

  // in scope

  @Test
  def testValInScope(): Unit =
    eval("++", 1, Java.primitives.int)

  @Test
  def testVarInScope(): Unit =
    testAssignment(on = "+:+", tpe = Java.primitives.int, values = "1", "2", "3")

  @Test
  def testNoArgDefInScope(): Unit =
    eval("!!!", 1, Java.primitives.int)

  @Test
  def testEmptyArgDefInScope(): Unit =
    eval("@@@()", 2, Java.primitives.int)

  @Test
  def testDefInScope(): Unit =
    eval("###(123)", 123, Java.primitives.int)

  @Test
  def testVarArgDefInScope(): Unit =
    eval("""$$$("a", "b", "c")""", List("a", "b", "c"), Scala.::)

  // local (in method)

  @Test
  def testLocalVal(): Unit =
    eval("--", "123", Java.primitives.int)

  @Test
  def testLocalVar(): Unit =
    testAssignment(on = "-:-", tpe = Java.String, values = s("1"), s("2"), s("3"))

  // constructors

  @Test
  def testClass(): Unit =
    eval("new %%%(12)", "%%%(12)", "debug.%%%")

  @Test
  def testClassWithVarArg(): Unit =
    eval("""new ^^^("a", "b", "c")""", "^^^(List(a, b, c))", "debug.^^^")

  // in closures

  @Test
  def localValInClosure(): Unit =
    eval("list.map(i => i + --)", List(124, 125, 126), Scala.::)

  @Test
  def valInClosure(): Unit =
    eval("list.map(i => i + ++)", List(2, 3, 4), Scala.::)

  @Test
  def valFromObjectInClosure(): Unit =
    eval("list.map(i => i + OperatorsObj.++)", List(2, 3, 4), Scala.::)

  @Test
  def valOnInstanceInClosure(): Unit =
    eval("list.map(i => i + operators.++)", List(2, 3, 4), Scala.::)

  // on object

  @Test
  def testValOnObject(): Unit =
    eval("OperatorsObj.++", 1, Java.primitives.int)

  @Test
  def testVarOnObject(): Unit =
    testAssignment(on = "OperatorsObj.+:+", tpe = Java.primitives.int, values = "1", "2", "3")

  @Test
  def testNoArgDefOnObject(): Unit =
    eval("OperatorsObj.!!!", 1, Java.primitives.int)

  @Test
  def testEmptyArgDefOnObject(): Unit =
    eval("OperatorsObj.@@@()", 2, Java.primitives.int)

  @Test
  def testDefOnObject(): Unit =
    eval("OperatorsObj.###(123)", 123, Java.primitives.int)

  @Test
  def testVarArgDefOnObject(): Unit =
    eval("""OperatorsObj.$$$("a", "b", "c")""", List("a", "b", "c"), Scala.::)

  // on instance

  @Test
  def testVal(): Unit =
    eval("operators.++", 1, Java.primitives.int)

  @Test
  def testVar(): Unit =
    testAssignment(on = "operators.+:+", tpe = Java.primitives.int, values = "1", "2", "3")

  @Test
  def testNoArgDef(): Unit =
    eval("operators.!!!", 1, Java.primitives.int)

  @Test
  def testEmptyArgDef(): Unit =
    eval("operators.@@@()", 2, Java.primitives.int)

  @Test
  def testDef(): Unit =
    eval("operators.###(123)", 123, Java.primitives.int)

  @Test
  def testVarArgDef(): Unit =
    eval("""operators.$$$("a", "b", "c")""", List("a", "b", "c"), Scala.::)

}

object OperatorsTest extends BaseIntegrationTestCompanion(TestValues.OperatorsTestCase)

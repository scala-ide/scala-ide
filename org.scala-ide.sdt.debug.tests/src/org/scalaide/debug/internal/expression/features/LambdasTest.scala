/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.JavaBoxed

class LambdasTest extends BaseIntegrationTest(LambdasTest) {

  private def testFunction(code: String, expectedValue: String, expectedTypePart: String) = {
    val (resString, resType) = runCode(code)
    assertEquals("Result value differs:", expectedValue, resString)
    assertTrue(resType.contains(expectedTypePart))
  }

  @Ignore("TODO - O-5160 - add support for closures")
  @Test
  def `(x: Int) => int + x`(): Unit = testFunction(" (x : Int) => int + x", "<function1>", "Function1v0")

  @Test
  def `(x: Int) => int.toString.mkString `(): Unit =
    testFunction(" (x : Int) => x.toString.mkString ", "<function1>", "CustomFunction")

  @Test
  def `((x: Int) => x.toString.mkString)(2) `(): Unit =
    testFunction(" ((x: Int) => x.toString.mkString)(2) ", "2", JavaBoxed.String)

  @Test
  def higherOrderfunctionWithMultipleParameterLists(): Unit =
    testFunction("List(1, 2, 3).fold(0)(_ + _)", "6", JavaBoxed.Integer)

  @Ignore("TODO - O-5266 - add support for generic types")
  @Test
  def `function and primitives: list.filter(_ >= 2) `(): Unit =
    testFunction("list.filter(_ >= 2)", "List(2,3)", JavaBoxed.String)

  @Ignore("TODO - O-5266 - add support for generic types")
  @Test
  def `function and primitives: list.filter(1 >) `(): Unit =
    testFunction("list.filter(1 >)", "List(2,3)", JavaBoxed.String)

  @Test
  def `libClass.perform(_ + 2) `(): Unit =
    testFunction("libClass.perform(_ + 2)", "3", JavaBoxed.Integer)

  @Test
  def `libClass.performByName(1 + 2) `(): Unit =
    testFunction("libClass.performByName(1 + 2)", "4", JavaBoxed.Integer)

  @Ignore("TODO - O-5160 - add support for closures")
  @Test
  def `libClass.performTwice(libClass.incrementAndGet()) `(): Unit =
    testFunction(" libClass.performTwice(libClass.incrementAndGet()) ", "3", JavaBoxed.Integer)

  @Test
  def `libClass.performByName("ala".mkString) `(): Unit =
    testFunction(""" libClass.performByNameGen("ala".mkString) """, "ala", JavaBoxed.String)

  @Test
  def `libClass.performByName("ala") `(): Unit =
    testFunction(""" libClass.performByNameGen("ala") """, "ala", JavaBoxed.String)
}

object LambdasTest extends BaseIntegrationTestCompanion
/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues

class NewKeywordTest extends BaseIntegrationTest(NewKeywordTest) {

  @Test
  def `new LibClassWithoutArgs`(): Unit = eval("new LibClassWithoutArgs", "LibClassWithoutArgs", "debug.LibClassWithoutArgs")

  @Test
  def `new LibClass(1)`(): Unit = eval("new LibClass(1)", "LibClass(1)", "debug.LibClass")

  @Test
  def `new LibClass2Lists(1)(2)`(): Unit =
    eval("new LibClass2Lists(1)(2)", "LibClass2Lists(1)", "debug.LibClass2Lists")

  @Test
  def createNewInstanceOfPrimitiveType(): Unit =
    eval("new java.lang.Integer(12345)", "12345", "java.lang.Integer")

  @Ignore("TODO - O-5117 - add support from varargs in constructor")
  @Test
  def `new LibClassWithVararg(1, 2)`(): Unit =
    eval("new LibClassWithVararg(1, 2)", "LibClassWithVararg(Seq(1, 2))", "debug.LibClassWithVararg")
}

object NewKeywordTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.newInstancesFileName,
  lineNumber = TestValues.newInstancesLineNumber)

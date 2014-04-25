/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.NewInstancesTestCase

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
    eval("new java.lang.Integer(12345)", "12345", Java.boxed.Integer)

  @Ignore("TODO - O-5117 - add support from varargs in constructor")
  @Test
  def `new LibClassWithVararg(1, 2)`(): Unit =
    eval("new LibClassWithVararg(1, 2)", "LibClassWithVararg(Seq(1, 2))", "debug.LibClassWithVararg")

  @Test
  def nestedInstantiatedClassField(): Unit =
    eval("(new LibObject.LibNestedClass).LibMoreNestedObject.id", "4", Java.boxed.Integer)
}

object NewKeywordTest extends BaseIntegrationTestCompanion(
  fileName = NewInstancesTestCase.fileName,
  lineNumber = NewInstancesTestCase.breakpointLine)

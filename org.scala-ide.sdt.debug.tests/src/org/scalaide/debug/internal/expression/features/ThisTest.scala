/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues

class ThisTest extends BaseIntegrationTest(ThisTest) {

  import TestValues._

  // IMPLICIT this tests

  @Test
  def implicitThisValFromTrait(): Unit = eval("traitParam", This.traitParam, JavaBoxed.Integer)

  @Test
  def implicitThisDefFromTrait(): Unit = eval("traitMethod()", This.traitMethod, JavaBoxed.Integer)

  @Test
  def implicitThisValFromClass(): Unit = eval("classParam", This.classParam, JavaBoxed.Integer)

  @Test
  def implicitThisDefFromClass(): Unit = eval("classMethod()", This.classMethod, JavaBoxed.Integer)

  @Test
  def implicitThisValFromObject(): Unit = eval("objectParam", This.objectParam, JavaBoxed.Integer)

  @Test
  def implicitThisDefFromObject(): Unit = eval("objectMethod()", This.objectMethod, JavaBoxed.Integer)

  // EXPLICIT this tests

  @Test
  def explicitThisValFromTrait(): Unit = eval("this.traitParam", This.traitParam, JavaBoxed.Integer)

  @Test
  def explicitThisDefFromTrait(): Unit = eval("this.traitMethod()", This.traitMethod, JavaBoxed.Integer)

  @Test
  def explicitThisValFromClass(): Unit = eval("this.classParam", This.classParam, JavaBoxed.Integer)

  @Test
  def explicitThisDefFromClass(): Unit = eval("this.classMethod()", This.classMethod, JavaBoxed.Integer)

  @Test
  def explicitThisValFromObject(): Unit = eval("this.objectParam", This.objectParam, JavaBoxed.Integer)

  @Test
  def explicitThisDefFromObject(): Unit = eval("this.objectMethod()", This.objectMethod, JavaBoxed.Integer)

}

object ThisTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.thisFileName,
  lineNumber = TestValues.thisLineNumber)

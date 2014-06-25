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

class VisibilityTest extends BaseIntegrationTest(VisibilityTest) {

  import TestValues._

  // IMPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisValFromTrait(): Unit = eval("traitParam", This.traitParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromTrait(): Unit = eval("traitMethod()", This.traitMethod, JavaBoxed.Integer)

  @Test
  def protectedImplicitThisValFromClass(): Unit = eval("classParam", This.classParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromClass(): Unit = eval("classMethod()", This.classMethod, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisValFromObject(): Unit = eval("objectParam", This.objectParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisDefFromObject(): Unit = eval("objectMethod()", This.objectMethod, JavaBoxed.Integer)

  // EXPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromTrait(): Unit = eval("this.traitParam", This.traitParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromTrait(): Unit = eval("this.traitMethod()", This.traitMethod, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromClass(): Unit = eval("this.classParam", This.classParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromClass(): Unit = eval("this.classMethod()", This.classMethod, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisValFromObject(): Unit = eval("this.objectParam", This.objectParam, JavaBoxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisDefFromObject(): Unit = eval("this.objectMethod()", This.objectMethod, JavaBoxed.Integer)

}

object VisibilityTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.visibilityFileName,
  lineNumber = TestValues.visibilityLineNumber)

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.TestValues.ThisTestCase

class ThisTest extends BaseIntegrationTest(ThisTest) {

  import ThisTestCase._

  // IMPLICIT this tests

  @Test
  def implicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.boxed.Integer)

  @Test
  def implicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.boxed.Integer)

  @Test
  def implicitThisValFromClass(): Unit = eval("classParam", classParam, Java.boxed.Integer)

  @Test
  def implicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.boxed.Integer)

  @Test
  def implicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.boxed.Integer)

  @Test
  def implicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.boxed.Integer)

  // EXPLICIT this tests

  @Test
  def explicitThisValFromTrait(): Unit = eval("this.traitParam", traitParam, Java.boxed.Integer)

  @Test
  def explicitThisDefFromTrait(): Unit = eval("this.traitMethod()", traitMethod, Java.boxed.Integer)

  @Test
  def explicitThisValFromClass(): Unit = eval("this.classParam", classParam, Java.boxed.Integer)

  @Test
  def explicitThisDefFromClass(): Unit = eval("this.classMethod()", classMethod, Java.boxed.Integer)

  @Test
  def explicitThisValFromObject(): Unit = eval("this.objectParam", objectParam, Java.boxed.Integer)

  @Test
  def explicitThisDefFromObject(): Unit = eval("this.objectMethod()", objectMethod, Java.boxed.Integer)

}

object ThisTest extends BaseIntegrationTestCompanion(ThisTestCase)

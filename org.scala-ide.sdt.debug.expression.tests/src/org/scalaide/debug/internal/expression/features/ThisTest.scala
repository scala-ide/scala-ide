/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.ThisTestCase

class ThisTest extends BaseIntegrationTest(ThisTest) {

  import ThisTestCase._

  // IMPLICIT this tests

  @Test
  def implicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.primitives.int)

  @Test
  def implicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.primitives.int)

  @Test
  def implicitThisValFromClass(): Unit = eval("classParam", classParam, Java.primitives.int)

  @Test
  def implicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.primitives.int)

  @Test
  def implicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.primitives.int)

  @Test
  def implicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.primitives.int)

  // EXPLICIT this tests

  @Test
  def explicitThisValFromTrait(): Unit = eval("this.traitParam", traitParam, Java.primitives.int)

  @Test
  def explicitThisDefFromTrait(): Unit = eval("this.traitMethod()", traitMethod, Java.primitives.int)

  @Test
  def explicitThisValFromClass(): Unit = eval("this.classParam", classParam, Java.primitives.int)

  @Test
  def explicitThisDefFromClass(): Unit = eval("this.classMethod()", classMethod, Java.primitives.int)

  @Test
  def explicitThisValFromObject(): Unit = eval("this.objectParam", objectParam, Java.primitives.int)

  @Test
  def explicitThisDefFromObject(): Unit = eval("this.objectMethod()", objectMethod, Java.primitives.int)

}

object ThisTest extends BaseIntegrationTestCompanion(ThisTestCase) with DefaultBeforeAfterAll

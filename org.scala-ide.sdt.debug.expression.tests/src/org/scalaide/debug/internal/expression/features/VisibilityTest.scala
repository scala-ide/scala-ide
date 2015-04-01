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
import org.scalaide.debug.internal.expression.TestValues.VisibilityTestCase

class VisibilityTest extends BaseIntegrationTest(VisibilityTest) {

  import VisibilityTestCase._

  // IMPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.boxed.Integer)

  @Test
  def protectedImplicitThisValFromClass(): Unit = eval("classParam", classParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.boxed.Integer)

  // EXPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromClass(): Unit = eval("classParam", classParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.boxed.Integer)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.boxed.Integer)

}

object VisibilityTest extends BaseIntegrationTestCompanion(VisibilityTestCase)

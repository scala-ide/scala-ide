/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.VisibilityTestCase

class VisibilityTest extends BaseIntegrationTest(VisibilityTest) {

  import VisibilityTestCase._

  // IMPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.primitives.int)

  @Test
  def protectedImplicitThisValFromClass(): Unit = eval("classParam", classParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedImplicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateImplicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.primitives.int)

  // EXPLICIT this tests

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromTrait(): Unit = eval("traitParam", traitParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromTrait(): Unit = eval("traitMethod()", traitMethod, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisValFromClass(): Unit = eval("classParam", classParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def protectedExplicitThisDefFromClass(): Unit = eval("classMethod()", classMethod, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisValFromObject(): Unit = eval("objectParam", objectParam, Java.primitives.int)

  @Ignore("TODO - O-5463 - Add support for private/protected methods and fields")
  @Test
  def privateExplicitThisDefFromObject(): Unit = eval("objectMethod()", objectMethod, Java.primitives.int)

}

object VisibilityTest extends BaseIntegrationTestCompanion(VisibilityTestCase) with DefaultBeforeAfterAll

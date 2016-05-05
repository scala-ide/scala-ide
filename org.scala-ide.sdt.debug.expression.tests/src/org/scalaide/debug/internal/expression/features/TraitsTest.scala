/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class TraitsTest extends BaseIntegrationTest(TraitsTest) with DefaultBeforeAfterEach {

  @Test
  def testThis(): Unit =
    eval("this", "C", "debug.C")

  @Test
  def testTraitValue(): Unit =
    eval("valInTrait", "valInTrait", Names.Java.String)

  @Test
  def testTraitMethod(): Unit =
    eval("defInTrait()", "defInTrait", Names.Java.String)

  @Test
  def testClassValue(): Unit =
    eval("valInClass", "valInClass", Names.Java.String)

  @Test
  def testClassMethod(): Unit =
    eval("defInClass()", "defInClass", Names.Java.String)
}

object TraitsTest extends BaseIntegrationTestCompanion(TestValues.TraitsTestCase) with DefaultBeforeAfterAll {

  override def typeName = "debug.T"
}

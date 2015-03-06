/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.junit.Test
import org.scalaide.debug.internal.expression.TestValues

class TraitsTest extends BaseIntegrationTest(TraitsTest) {

  @Test
  def testThis(): Unit =
    eval("this", "C", "debug.C")

  @Test
  def testTraitValue(): Unit =
    eval("valInTrait", "valInTrait", Names.Java.boxed.String)

  @Test
  def testTraitMethod(): Unit =
    eval("defInTrait()", "defInTrait", Names.Java.boxed.String)

  @Test
  def testClassValue(): Unit =
    eval("valInClass", "valInClass", Names.Java.boxed.String)

  @Test
  def testClassMethod(): Unit =
    eval("defInClass()", "defInClass", Names.Java.boxed.String)
}

object TraitsTest extends BaseIntegrationTestCompanion(TestValues.TraitsTestCase) {

  override def typeName = "debug.T"
}

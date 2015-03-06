/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class MultipleParametersListTest extends BaseIntegrationTest(MultipleParametersListTest) {

  @Test
  def testMultipleParametersFunction(): Unit =
    eval("Libs.libMultipleParamers(int)(int)", "2", Java.boxed.Integer)

  @Test
  def testFullNameMultipleParametersFunction(): Unit =
    eval("debug.Libs.libMultipleParamers(int)(int)", "2", Java.boxed.Integer)
}

object MultipleParametersListTest extends BaseIntegrationTestCompanion
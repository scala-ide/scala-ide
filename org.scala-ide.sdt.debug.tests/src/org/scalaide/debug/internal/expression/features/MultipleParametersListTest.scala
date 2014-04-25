/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class MultipleParametersListTest extends BaseIntegrationTest(MultipleParametersListTest) {

  @Test
  def `multiple parameters function`(): Unit =
    eval("Libs.libMultipleParamers(int)(int)", "2", JavaBoxed.Integer)

  @Test
  def `full name multiple parameters function`(): Unit =
    eval("debug.Libs.libMultipleParamers(int)(int)", "2", JavaBoxed.Integer)
}

object MultipleParametersListTest extends BaseIntegrationTestCompanion
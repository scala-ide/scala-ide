/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class BooleanComparisonTest extends BaseIntegrationTest(BooleanComparisonTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def `!boolean`(): Unit = eval("!boolean", !boolean, JavaBoxed.Boolean)

  @Test
  def equality(): Unit = {
    eval("boolean == boolean", boolean == boolean, JavaBoxed.Boolean)
    eval("boolean == boolean2", boolean == boolean2, JavaBoxed.Boolean)
    eval("boolean2 == boolean2", boolean == boolean, JavaBoxed.Boolean)
    eval("boolean2 == boolean", boolean == boolean2, JavaBoxed.Boolean)
  }

  @Test
  def inequality(): Unit = {
    eval("boolean != boolean", boolean != boolean, JavaBoxed.Boolean)
    eval("boolean != boolean2", boolean != boolean2, JavaBoxed.Boolean)
    eval("boolean2 != boolean2", boolean != boolean, JavaBoxed.Boolean)
    eval("boolean2 != boolean", boolean != boolean2, JavaBoxed.Boolean)
  }

  @Ignore("TODO - O-5375 - support for boolean comparison")
  @Test
  def lesserThan(): Unit = {
    eval("boolean < boolean", boolean < boolean, JavaBoxed.Boolean)
    eval("boolean2 < boolean2", boolean < boolean, JavaBoxed.Boolean)
    eval("boolean < boolean2", boolean < boolean2, JavaBoxed.Boolean)
    eval("boolean2 < boolean", boolean < boolean2, JavaBoxed.Boolean)
  }

  @Ignore("TODO - O-5375 - support for boolean comparison")
  @Test
  def greaterThan(): Unit = {
    eval("boolean > boolean", boolean > boolean, JavaBoxed.Boolean)
    eval("boolean > boolean2", boolean > boolean2, JavaBoxed.Boolean)
    eval("boolean2 > boolean2", boolean > boolean, JavaBoxed.Boolean)
    eval("boolean2 > boolean", boolean > boolean2, JavaBoxed.Boolean)
  }

  @Ignore("TODO - O-5375 - support for boolean comparison")
  @Test
  def lesserThanOrEqual(): Unit = {
    eval("boolean <= boolean", boolean <= boolean, JavaBoxed.Boolean)
    eval("boolean <= boolean2", boolean <= boolean2, JavaBoxed.Boolean)
    eval("boolean2 <= boolean2", boolean <= boolean, JavaBoxed.Boolean)
    eval("boolean2 <= boolean", boolean <= boolean2, JavaBoxed.Boolean)
  }

  @Ignore("TODO - O-5375 - support for boolean comparison")
  @Test
  def greaterThanOrEqual(): Unit = {
    eval("boolean >= boolean", boolean >= boolean, JavaBoxed.Boolean)
    eval("boolean >= boolean2", boolean >= boolean2, JavaBoxed.Boolean)
    eval("boolean2 >= boolean2", boolean >= boolean, JavaBoxed.Boolean)
    eval("boolean2 >= boolean", boolean >= boolean2, JavaBoxed.Boolean)
  }

}

object BooleanComparisonTest extends BaseIntegrationTestCompanion

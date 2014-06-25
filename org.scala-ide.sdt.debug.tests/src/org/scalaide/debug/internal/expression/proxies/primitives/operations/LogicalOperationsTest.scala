/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class LogicalOperationsTest extends BaseIntegrationTest(LogicalOperationsTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def `!boolean`(): Unit = eval("!boolean", !boolean, JavaBoxed.Boolean)

  @Test
  def `boolean || boolean`(): Unit = eval("boolean || boolean2", boolean || boolean2, JavaBoxed.Boolean)

  @Test
  def `boolean && boolean`(): Unit = eval("boolean && boolean2", boolean && boolean2, JavaBoxed.Boolean)

  @Test
  def `boolean | boolean`(): Unit = eval("boolean | boolean2", boolean | boolean2, JavaBoxed.Boolean)

  @Test
  def `boolean & boolean`(): Unit = eval("boolean & boolean2", boolean & boolean2, JavaBoxed.Boolean)

  @Test
  def `boolean ^ boolean`(): Unit = eval("boolean ^ boolean2", boolean ^ boolean2, JavaBoxed.Boolean)
}

object LogicalOperationsTest extends BaseIntegrationTestCompanion

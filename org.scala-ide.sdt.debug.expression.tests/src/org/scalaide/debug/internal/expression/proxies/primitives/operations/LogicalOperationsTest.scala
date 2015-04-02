/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class LogicalOperationsTest extends BaseIntegrationTest(LogicalOperationsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def `!boolean`(): Unit = eval("!boolean", !boolean, Java.boxed.Boolean)

  @Test
  def `boolean || boolean`(): Unit = eval("boolean || boolean2", boolean || boolean2, Java.boxed.Boolean)

  @Test
  def `boolean && boolean`(): Unit = eval("boolean && boolean2", boolean && boolean2, Java.boxed.Boolean)

  @Test
  def `boolean | boolean`(): Unit = eval("boolean | boolean2", boolean | boolean2, Java.boxed.Boolean)

  @Test
  def `boolean & boolean`(): Unit = eval("boolean & boolean2", boolean & boolean2, Java.boxed.Boolean)

  @Test
  def `boolean ^ boolean`(): Unit = eval("boolean ^ boolean2", boolean ^ boolean2, Java.boxed.Boolean)
}

object LogicalOperationsTest extends BaseIntegrationTestCompanion

/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.ToolBoxBugsTestCase

class ToolBoxBugsTest extends BaseIntegrationTest(ToolBoxBugsTest) with DefaultBeforeAfterEach {

  @Test(expected = classOf[LambdaCompilationFailure])
  def methodAsGetOrElseParam(): Unit = eval("None.getOrElse(zero)", "0", Java.boxed.Integer)
}

object ToolBoxBugsTest extends BaseIntegrationTestCompanion(ToolBoxBugsTestCase) with DefaultBeforeAfterAll

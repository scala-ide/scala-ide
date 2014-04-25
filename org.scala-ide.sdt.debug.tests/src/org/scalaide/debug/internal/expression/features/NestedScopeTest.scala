/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.TestValues.NestedTestCase

class NestedScopeTest extends BaseIntegrationTest(NestedScopeTest) {

  import TestValues.any2String
  import NestedTestCase._

  @Test
  def testUsedVariableIsVisibleInInnerScope(): Unit = eval("outerUsed", outerUsed, Java.boxed.Integer)

  @Test(expected = classOf[scala.tools.reflect.ToolBoxError])
  def testUnusedVariableIsNotVisibleInInnerScope(): Unit = eval("outerUnused", outerUnused, Java.boxed.Integer)
}

object NestedScopeTest extends BaseIntegrationTestCompanion("nested-scope", NestedTestCase.fileName, NestedTestCase.breakpointLine)
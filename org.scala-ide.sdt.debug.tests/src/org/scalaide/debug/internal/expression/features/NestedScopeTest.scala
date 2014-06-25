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
import org.scalaide.debug.internal.expression.TestValues

class NestedScopeTest extends BaseIntegrationTest(NestedScopeTest) {

  import TestValues.Nested._
  import TestValues.any2String

  @Test
  def testUsedVariableIsVisibleInInnerScope(): Unit = eval("outerUsed", outerUsed, JavaBoxed.Integer)

  @Test(expected = classOf[scala.tools.reflect.ToolBoxError])
  def testUnusedVariableIsNotVisibleInInnerScope(): Unit = eval("outerUnused", outerUnused, JavaBoxed.Integer)
}

object NestedScopeTest extends BaseIntegrationTestCompanion("nested-scope", TestValues.nestedFileName, TestValues.nestedScopeLine)
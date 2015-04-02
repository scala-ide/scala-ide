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
import org.scalaide.debug.internal.expression.ReflectiveCompilationFailure

class NestedScopeTest extends BaseIntegrationTest(NestedScopeTest) {

  import NestedTestCase._

  @Test
  def testUsedVariableIsVisibleInInnerScope(): Unit = eval("outerUsed", outerUsed, Java.boxed.Integer)

  @Test(expected = classOf[ReflectiveCompilationFailure])
  def testUnusedVariableIsNotVisibleInInnerScope(): Unit = eval("outerUnused", outerUnused, Java.boxed.Integer)
}

object NestedScopeTest extends BaseIntegrationTestCompanion(NestedTestCase)

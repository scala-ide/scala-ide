/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.ReflectiveCompilationFailure
import org.scalaide.debug.internal.expression.TestValues.NestedTestCase
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class NestedScopeTest extends BaseIntegrationTest(NestedScopeTest) with DefaultBeforeAfterEach {

  import NestedTestCase._

  @Test
  def testUsedVariableIsVisibleInInnerScope(): Unit = eval("outerUsed", outerUsed, Java.primitives.int)

  @Test(expected = classOf[ReflectiveCompilationFailure])
  def testUnusedVariableIsNotVisibleInInnerScope(): Unit = eval("outerUnused", outerUnused, Java.primitives.int)
}

object NestedScopeTest extends BaseIntegrationTestCompanion(NestedTestCase) with DefaultBeforeAfterAll

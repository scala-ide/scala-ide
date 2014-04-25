/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.expression.conditional.ConditionalBreakpointsTest
import org.scalaide.debug.internal.expression.features.FeaturesTestSuite
import org.scalaide.debug.internal.expression.mocks.TypeSearchMockTest
import org.scalaide.debug.internal.expression.proxies.phases.PhasesTestSuite
import org.scalaide.debug.internal.expression.proxies.primitives.PrimitivesOperationsTestSuite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[ExpressionManagerTest],
    classOf[FeaturesTestSuite],
    classOf[PrimitivesOperationsTestSuite],
    classOf[DifferentStackFramesTest],
    classOf[PhasesTestSuite],
    classOf[TypeSearchMockTest],
    classOf[ConditionalBreakpointsTest]))
class ExpressionsTestSuite
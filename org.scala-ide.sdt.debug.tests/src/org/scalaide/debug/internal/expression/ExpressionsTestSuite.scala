/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.expression.features.FeaturesTestSuite
import org.scalaide.debug.internal.expression.proxies.phases.PhasesTestSuite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[ExpressionManagerTest],
    classOf[FeaturesTestSuite],
    classOf[DifferentStackFramesTest],
    classOf[PhasesTestSuite]))
class ExpressionsTestSuite
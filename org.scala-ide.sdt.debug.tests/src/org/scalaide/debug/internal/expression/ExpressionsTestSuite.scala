/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.expression.conditional.ConditionalBreakpointsTest
import org.scalaide.debug.internal.expression.features.FeaturesTestSuite
import org.scalaide.debug.internal.expression.proxies.phases.ProxiesTestSuite
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
    classOf[ProxiesTestSuite],
    classOf[ConditionalBreakpointsTest]))
class ExpressionsTestSuite
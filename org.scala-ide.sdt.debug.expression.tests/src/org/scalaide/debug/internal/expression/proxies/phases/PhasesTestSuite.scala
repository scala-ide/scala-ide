/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for expression evaluator tests which tests phases in isolation.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[TypeExtractionTest],
    classOf[VariableProxiesTest]))
class PhasesTestSuite

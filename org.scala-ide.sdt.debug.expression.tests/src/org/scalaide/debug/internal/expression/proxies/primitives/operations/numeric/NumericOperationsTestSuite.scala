/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[AdditionTest],
    classOf[DivisionTest],
    classOf[ModuloTest],
    classOf[MultiplicationTest],
    classOf[SubtractionTest]))
class NumericOperationsTestSuite

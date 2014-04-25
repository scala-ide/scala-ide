/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.expression.proxies.primitives.operations._
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseOperationsTestSuite
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.NumericOperationsTestSuite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[TypeConversionsTest],
    classOf[BooleanComparisonTest],
    classOf[ComplexOperationsTest],
    classOf[LogicalOperationsTest],
    classOf[NumericComparisonTest],
    classOf[UnaryOperatorsTest],
    classOf[BitwiseOperationsTestSuite],
    classOf[NumericOperationsTestSuite]))
class PrimitivesOperationsTestSuite

/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
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

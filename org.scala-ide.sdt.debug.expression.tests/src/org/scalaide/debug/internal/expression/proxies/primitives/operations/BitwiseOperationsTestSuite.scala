/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Junit test suite for the Scala debugger.
 */
@RunWith(classOf[Suite])
@Suite.SuiteClasses(
  Array(
    classOf[BitwiseAndTest],
    classOf[BitwiseOrTest],
    classOf[BitwiseShiftRightTest],
    classOf[BitwiseShiftLeftWithZerosTest],
    classOf[BitwiseShiftRightWithZerosTest],
    classOf[BitwiseXorTest]))
class BitwiseOperationsTestSuite

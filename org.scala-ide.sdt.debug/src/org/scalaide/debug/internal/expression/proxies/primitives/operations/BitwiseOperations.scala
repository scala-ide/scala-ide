/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseAnd
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseOr
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseShiftLeftWithZeros
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseShiftRight
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseShiftRightWithZeros
import org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise.BitwiseXor

/**
 * Aggregates implementation of bitwise OR, AND and XOR operations for primitive proxies.
 */
trait BitwiseOperations
  extends BitwiseOr
  with BitwiseAnd
  with BitwiseXor
  with BitwiseShiftLeftWithZeros
  with BitwiseShiftRightWithZeros
  with BitwiseShiftRight { self: IntegerNumberJdiProxy[_, _] => }
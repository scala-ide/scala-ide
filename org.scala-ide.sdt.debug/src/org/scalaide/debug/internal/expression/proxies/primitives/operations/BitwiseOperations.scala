/*
 * Copyright (c) 2014 Contributor. All rights reserved.
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
trait BitwiseOperations[Primitive, Proxy <: IntegerNumberJdiProxy[Primitive, Proxy]]
  extends BitwiseOr[Proxy]
  with BitwiseAnd[Proxy]
  with BitwiseXor[Proxy]
  with BitwiseShiftLeftWithZeros[Proxy]
  with BitwiseShiftRightWithZeros[Proxy]
  with BitwiseShiftRight[Proxy] { self: Proxy => }
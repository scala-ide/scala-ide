/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.FloatingPointNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointAddition
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointDivision
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointModulo
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointMultiplication
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointSubtraction
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerAddition
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerDivision
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerModulo
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerMultiplication
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerSubtraction

/**
 * Aggregates numeric operations on primitive proxies types.
 */
trait IntegerNumericOperations[Primitive, Proxy <: IntegerNumberJdiProxy[Primitive, Proxy]]
  extends IntegerAddition[Proxy]
  with IntegerSubtraction[Proxy]
  with IntegerMultiplication[Proxy]
  with IntegerDivision[Proxy]
  with IntegerModulo[Proxy] { self: Proxy => }

trait FloatingPointNumericOperations[Primitive, Proxy <: FloatingPointNumberJdiProxy[Primitive, Proxy]]
  extends FloatingPointAddition[Proxy]
  with FloatingPointSubtraction[Proxy]
  with FloatingPointMultiplication[Proxy]
  with FloatingPointDivision[Proxy]
  with FloatingPointModulo[Proxy] { self: Proxy => }

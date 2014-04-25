/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerModulo
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointModulo
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerDivision
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointDivision
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerMultiplication
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointMultiplication
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerSubtraction
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointSubtraction
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.IntegerAddition
import org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric.FloatingPointAddition

/**
 * Aggregates numeric operations on primitive proxies types.
 */
trait IntegerNumericOperations
  extends IntegerAddition
  with IntegerSubtraction
  with IntegerMultiplication
  with IntegerDivision
  with IntegerModulo { self: IntegerNumberJdiProxy[_, _] => }

trait FloatingPointNumericOperations
  extends FloatingPointAddition
  with FloatingPointSubtraction
  with FloatingPointMultiplication
  with FloatingPointDivision
  with FloatingPointModulo { self: NumberJdiProxy[_, _] => }

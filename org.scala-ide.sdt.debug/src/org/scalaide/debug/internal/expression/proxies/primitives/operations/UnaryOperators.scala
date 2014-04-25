/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.NumberJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy

trait UnaryMinus[Proxy <: NumberJdiProxy[_, _]] {
  def unary_- : Proxy
}

trait UnaryPlus[Proxy <: NumberJdiProxy[_, _] with UnaryMinus[Proxy]] {
  self: UnaryMinus[Proxy] =>

  def unary_+ : Proxy = this.unary_-.unary_-
}

trait UnaryBitwiseNegation {
  self: IntegerNumberJdiProxy[_, _] =>

  def unary_~ : IntegerNumberJdiProxy[_, _]
}

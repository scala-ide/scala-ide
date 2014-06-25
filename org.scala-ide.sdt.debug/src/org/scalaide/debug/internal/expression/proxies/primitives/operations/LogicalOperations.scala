/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy

trait LogicalOperations { self: BooleanJdiProxy =>

  final def ||(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue || that.booleanValue, context)

  final def &&(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue && that.booleanValue, context)

  final def |(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue | that.booleanValue, context)

  final def &(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue & that.booleanValue, context)

  final def ^(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue ^ that.booleanValue, context)

  final def unary_! = BooleanJdiProxy.fromPrimitive(!booleanValue, context)
}
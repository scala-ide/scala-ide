/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy

trait LogicalOperations { self: BooleanJdiProxy =>

  final def ||(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue || that.booleanValue, proxyContext)

  final def &&(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue && that.booleanValue, proxyContext)

  final def |(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue | that.booleanValue, proxyContext)

  final def &(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue & that.booleanValue, proxyContext)

  final def ^(that: BooleanJdiProxy): BooleanJdiProxy =
    BooleanJdiProxy.fromPrimitive(this.booleanValue ^ that.booleanValue, proxyContext)

  final def unary_! = BooleanJdiProxy.fromPrimitive(!booleanValue, proxyContext)
}
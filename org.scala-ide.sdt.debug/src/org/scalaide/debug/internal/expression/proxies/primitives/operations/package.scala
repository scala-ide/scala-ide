/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives

/**
 * Contains numeric and bitwise operations implementation overloaded for all primitive types.
 *
 * Numeric addition, subtraction, multiplication, division and modulo are aggregated under
 * [[org.scalaide.debug.internal.expression.proxies.primitives.operations.NumericOperations]].
 *
 * Bitwise OR, AND and XOR are aggregated under [[org.scalaide.debug.internal.expression.proxies.primitives.operations.BitwiseOperations]].
 *
 * Comparison (<, >, <= and >=) is implemented in [[org.scalaide.debug.internal.expression.proxies.primitives.operations.BooleanComparison]]
 * and [[org.scalaide.debug.internal.expression.proxies.primitives.operations.NumericComparison]].
 *
 * Logical operations (for Boolean proxies) are implemented in [[org.scalaide.debug.internal.expression.proxies.primitives.operations.LogicalOperations]].
 */
package object operations
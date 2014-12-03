/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

/**
 * Contains implementations of [[org.scalaide.debug.internal.expression.proxies.JdiProxy]] for JVM primitives.
 *
 * Those types are used to proxy calls to boxed values in expression evaluation to handle synthetic method calls.
 *
 * That includes:
 *
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy]]
 *  $ [[org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy]]
 *
 * All extends [[org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy]], which defines common interface for them,
 * and for numeric operations also [[org.scalaide.debug.internal.expression.proxies.primitives.IntegerNumberJdiProxy]] or
 * [[org.scalaide.debug.internal.expression.proxies.primitives.FloatingPointNumberJdiProxy]].
 *
 * Their companion object extends [[org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxyCompanion]], which defines common methods
 * and simplifies implementation.
 */
package object primitives {
  import org.scalaide.debug.internal.expression.{ proxies => parent }

  /** Name of this package, used in reflective compilation of expressions. */
  val name = s"${parent.name}.primitives"
}
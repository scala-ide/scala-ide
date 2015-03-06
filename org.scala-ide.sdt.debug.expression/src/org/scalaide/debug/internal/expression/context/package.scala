/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

/**
 * Contains [[org.scalaide.debug.internal.expression.context.JdiContext]], that represents context of
 * debugged jvm (on some breakpoint). It allows one to create proxies for variables,
 * evaluate methods on debugged jvm and to inspect scope of debug for declared values and objects.
 *
 * For brevity, implementation is split into several files:
 *
 * $ [[org.scalaide.debug.internal.expression.context.JdiMethodInvoker]] which implements method invocation,
 * $ [[org.scalaide.debug.internal.expression.context.JdiClassLoader]] which supports loading classes remotely,
 * $ [[org.scalaide.debug.internal.expression.context.JdiVariableContext]] which gives you access to variables from scope,
 * $ [[org.scalaide.debug.internal.expression.context.Stringifier]] which helps with changing proxies to their string representations,
 * $ [[org.scalaide.debug.internal.expression.context.Seeker]] which searches for classes, objects and methods,
 * $ and [[org.scalaide.debug.internal.expression.context.Proxyfier]] which creates proxies for values, objects etc.
 */
package object context {

  /** Name of this package, used in reflective compilation of expressions. */
  val name = "org.scalaide.debug.internal.expression.context"
}
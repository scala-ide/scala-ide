package org.scalaide.debug.internal.async

/** A fully qualified name of a method, on whose entry we want to capture
 *  a stack trace.
 *
 *  @param className the fully qualified class name
 *  @param methodName the method name
 *  @param paramIdx  the index of the method parameter to be associated with a stackframe
 */
case class AsyncProgramPoint(className: String, methodName: String, paramIdx: Int)

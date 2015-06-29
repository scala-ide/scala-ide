package org.scalaide.logging

/** Available log levels. */
object Level extends Enumeration {

  /** Log level for debugging messages.
   */
  val DEBUG = Value(0)

  /** Log level for information messages.
   */
  val INFO = Value(1)

  /** Log level for warning messages.
   */
  val WARN = Value(2)

  /** Log level for error messages.
   */
  val ERROR = Value(3)

  /** Log level for fatal messages.
   */
  val FATAL = Value(4)

  /** Log level for tracing messages.
   */
  val TRACE = Value(5)
}

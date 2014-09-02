package org.scalaide.logging

/** Available log levels. */
object Level extends Enumeration {
  /** Log level for debugging messages.
   */
  val DEBUG = Value

  /** Log level for information messages.
   */
  val INFO = Value

  /** Log level for warning messages.
   */
  val WARN = Value

  /** Log level for error messages.
   */
  val ERROR = Value

  /** Log level for fatal messages.
   */
  val FATAL = Value
}

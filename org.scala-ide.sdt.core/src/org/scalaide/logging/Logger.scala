package org.scalaide.logging

/** Defines the contract for implementing a Logger.*/
trait Logger {

  /** Logs a message at the INFO level.
   */
  def info(message: => Any)

  /** Logs a message with exception at the INFO level.
   */
  def info(message: => Any, t: Throwable)

  /** Logs a message at the DEBUG level.
   */
  def debug(message: => Any)

  /** Logs a message with exception at the DEBUG level.
   */
  def debug(message: => Any, t: Throwable)

  /** Logs a message at the WARN level.
   */
  def warn(message: => Any)

  /** Logs a message with exception at the WARN level.
   */
  def warn(message: => Any, t: Throwable)

  /** Logs a message at the ERROR level.
   */
  def error(message: => Any)

  /** Logs a message with exception at the ERROR level.
   */
  def error(message: => Any, t: Throwable)

  /** Logs a message at the FATAL level.
   */
  def fatal(message: => Any)

  /** Logs a message with exception at the FATAL level.
   */
  def fatal(message: => Any, t: Throwable)
}

package org.scalaide.logging

/** Defines the contract for implementing a Logger.*/
trait Logger {
  def info(message: => Any)
  def info(message: => Any, t: Throwable)

  def debug(message: => Any)
  def debug(message: => Any, t: Throwable)

  def warn(message: => Any)
  def warn(message: => Any, t: Throwable)

  def error(message: => Any)
  def error(message: => Any, t: Throwable)

  def fatal(message: => Any)
  def fatal(message: => Any, t: Throwable)
}

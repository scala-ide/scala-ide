package scala.tools.eclipse.util

trait Logger {
  def info(message: String)
  def debug(message: String)
  def warning(message: String)
  def error(t: Throwable)
  def error(message: String, t: Throwable)
}
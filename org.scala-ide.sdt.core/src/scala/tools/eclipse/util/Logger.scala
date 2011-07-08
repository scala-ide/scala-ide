package scala.tools.eclipse.util

trait Logger {
  object Category extends Enumeration {
    val INFO, ERROR = Value
  }

  import Category._
  def log(message: String, cat: Value = INFO) = {
    val printer = if (cat eq INFO) System.out else System.err
    printer.format("[%s] %s%n", getClass.getSimpleName, message)
  }
}
package scala.tools.eclipse.util

trait Logger {
  object Category extends Enumeration {
    val INFO, DEBUG, ERROR = Value
  }

  import Category._
  def log(message: String, cat: Value = INFO) = {
    val printer = if (cat eq ERROR) System.err else System.out 
    val name = if (getClass().isAnonymousClass) getClass.getName else getClass.getSimpleName
    
    printer.format("[%s] %s%n", name, message)
  }
  
  def debug(message: String) = log(message, DEBUG)
  def info(message: String) = log(message, INFO)
  def error(message: String) = log(message, ERROR)
}
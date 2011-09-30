package scala.tools.eclipse.util

trait Logger {
  def debug(message: => String)
  def info(message: => String)
  def error(message: => String)
}

/** 
 * The `DefaultLogger` is a minimalist implementation of a logger that 
 * prepends the class name to passed message. All outputs happens in the 
 * standard out.
 * 
 * By making this implementation package private we avoid that it leaks out 
 * (only the Logger's interface is public), so please do not change this.
 * 
 * [mirco] In the future the default logger should become somewhat more robust and 
 * easy to configure, I think we should switch to Log4J or similar. 
 * */
private[util] object DefaultLogger extends Logger {
  object Category extends Enumeration {
    val INFO, DEBUG, ERROR = Value
  }

  import Category._
  private def log(message: String, cat: Value = INFO) = {
    val printer = if (cat eq ERROR) System.err else System.out 
    val name = if (getClass().isAnonymousClass) getClass.getName else getClass.getSimpleName
    
    printer.format("[%s] %s%n", name, message)
  }
  
  override def debug(message: => String) = log(message, DEBUG)
  override def info(message: => String) = log(message, INFO)
  override def error(message: => String) = log(message, ERROR)
}
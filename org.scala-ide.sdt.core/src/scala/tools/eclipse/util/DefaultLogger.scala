package scala.tools.eclipse.util

import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.IStatus
import scala.util.control.ControlThrowable

private[util] object DefaultLogger {
  def apply(clazz: Class[_]): Logger = {
   val name = if(clazz.isAnonymousClass()) clazz.getName else clazz.getSimpleName
   new DefaultLogger(name)
  }
}

/**
 * The `DefaultLogger` is a minimal implementation of a logger. 
 * On the one hand, calling {{{info}}} or {{{debug}}} will output 
 * in the standard out the passed message. 
 * On the other hand, calling {{{warning}}} or {{{error}}} will push the 
 * passed message in the Eclipse Error View component.
 *
 * By making this implementation package private we avoid that it leaks out
 * (only the Logger's interface is public), so please do not change this.
 *
 * [In the future the default logger should become somewhat more robust and
 * easy to configure, using Log4J or similar seem a good solution.
 */
private class DefaultLogger(name: String) extends Logger {
  private object Category extends Enumeration {
    val INFO, DEBUG, ERROR = Value
  }

  import Category._

  override def info(message: String) = log(message, INFO)
  override def debug(message: String) = log(message, DEBUG)

  private def log(message: String, cat: Value = INFO) = {
    val printer = if (cat eq ERROR) System.err else System.out

    printer.format("[%s] %s%n", name, message)
  }

  import ScalaPlugin.plugin

  override def warning(msg: String): Unit = ScalaPlugin.plugin.getLog.log(new Status(IStatus.WARNING, plugin.pluginId, msg))

  override def error(t: Throwable): Unit = error(t.getClass + ":" + t.getMessage, t)

  override def error(msg: String, t: Throwable): Unit = {
    val t1 = if (t != null) t else { val ex = new Exception; ex.fillInStackTrace; ex }
    val status1 = new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, IStatus.ERROR, msg, t1)
    plugin.getLog.log(status1)

    val status = t match {
      case ce: ControlThrowable =>
        val t2 = { val ex = new Exception; ex.fillInStackTrace; ex }
        val status2 = new Status(
          IStatus.ERROR, plugin.pluginId, IStatus.ERROR,
          "Incorrectly logged ControlThrowable: " + ce.getClass.getSimpleName + "(" + ce.getMessage + ")", t2)
        plugin.getLog.log(status2)
      case _ =>
    }
  }
}
package scala.tools.eclipse.buildmanager.sbtintegration

import scala.tools.eclipse.buildmanager.BuildReporter

import scala.tools.nsc.util.{ Position, NoPosition, FakePos }
import scala.collection.mutable

trait FlushableLogger extends sbt.Logger {
  def flush(): Unit
}

/** An Sbt logger that publishes java errors to the underlying build reporter. */
class SbtBuildLogger(underlying: BuildReporter) extends FlushableLogger {
  // This needs to be improved but works for most of the java errors
  val javaErrorBegin = ".*\\.java:(\\d+):.*".r
  val buff = mutable.ListBuffer[Tuple2[sbt.Level.Value, String]]()
  def trace(t: => Throwable) {
    // ignore for now?
  }
  def success(message: => String) { }
  def log(level: sbt.Level.Value, message: => String) {
    import sbt.Level.{Info, Warn, Error, Debug}
    level match {
      case Info => ()
      case Warn => buff += ((level, message))  //underlying.warning(NoPosition, message)
      case Error => buff += ((level, message)) //underlying.error(NoPosition, message)
      case Debug => ()
    }
  }

  // This will at least ensure that we print log in the order required by eclipse for java problems
  // This is a temporary solution until the parsing of java error/warning messages is done correctly
  def flush() {
    def publishMsg(level: sbt.Level.Value, msg: String) = {
      level match {
          case sbt.Level.Warn  =>
            underlying.warning(NoPosition, msg)
          case sbt.Level.Error =>
            underlying.error(NoPosition, msg)
        }
    }
    import scala.collection
    val localBuff = new mutable.ListBuffer[String]()
    val buff0 = buff.dropRight(1) // remove number of error message
    var lastLevel: sbt.Level.Value = null
    buff0.foreach(msg => {
      val res = msg._2 match { case javaErrorBegin(_) => true; case _ => false }
      
      if ((msg._1 != lastLevel || res) && !localBuff.isEmpty) {
        assert(lastLevel != null)
        publishMsg(lastLevel, localBuff.mkString("\n"))
        localBuff.clear()
      }
      lastLevel = msg._1
      localBuff.append(msg._2)
    })
    if (!localBuff.isEmpty)
      publishMsg(lastLevel, localBuff.mkString("\n"))
  }
}

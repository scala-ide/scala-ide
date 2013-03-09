package scala.tools.eclipse.buildmanager.sbtintegration

import scala.collection.mutable
import scala.tools.eclipse.buildmanager.BuildReporter
import scala.tools.nsc.util.{ BatchSourceFile, OffsetPosition }
import scala.tools.nsc.util.{ Position, NoPosition }
import scala.tools.nsc.reporters.Reporter
import scala.tools.eclipse.util.EclipseResource

/**  An Sbt Reporter that forwards to an underlying [[BuildReporter]]
 */
private[sbtintegration] class SbtBuildReporter(underlying: BuildReporter) extends xsbti.ExtendedReporter {
  val probs = new mutable.ArrayBuffer[xsbti.Problem]

  def reset() = {
    underlying.reset
    probs.clear()
  }
  def hasErrors() = underlying.hasErrors
  def hasWarnings() = underlying.hasWarnings
  def printSummary() {} //TODO
  def problems: Array[xsbti.Problem] = probs.toArray
  def comment(pos: xsbti.Position, msg: String) {
    underlying.comment(toScalaPosition(pos), msg)
  }

  def m2o[T](m: xsbti.Maybe[T]): Option[T] = if (m.isEmpty) None else Some(m.get)
  def toScalaPosition(pos0: xsbti.Position): Position =
    (m2o(pos0.sourcePath), m2o(pos0.offset)) match {
      case (Some(srcpath), Some(offset)) =>
        EclipseResource.fromString(srcpath, underlying.project0.underlying.getFullPath) map { ifile =>
          val sourceFile = new BatchSourceFile(ifile)
          new OffsetPosition(sourceFile, offset.intValue)
        } getOrElse NoPosition
      case _ => NoPosition
    }

  def log(pos: xsbti.Position, msg: String, sev: xsbti.Severity) {
    probs += new xsbti.Problem {
      def severity() = sev
      def message() = msg
      def position() = pos
      def category() = "compile"
    }

    import xsbti.Severity.{Info, Warn, Error}
    val scalaPos = toScalaPosition(pos)
    sev match
    {
      case Info  => underlying.info(scalaPos, msg, false)
      case Warn  => underlying.warning(scalaPos, msg)
      case Error => underlying.error(scalaPos, msg)
    }
  }
}

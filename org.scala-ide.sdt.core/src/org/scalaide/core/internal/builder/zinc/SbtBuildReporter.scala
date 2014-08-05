package org.scalaide.core.internal.builder.zinc

import scala.collection.mutable
import scala.tools.nsc.reporters.Reporter
import org.scalaide.core.resources.EclipseResource
import org.scalaide.util.internal.SbtUtils.m2o
import org.eclipse.core.resources.IMarker
import xsbti.{ Position, Severity }
import org.scalaide.util.internal.SbtUtils
import org.scalaide.util.internal.eclipse.FileUtils
import org.eclipse.core.runtime.Path
import org.scalaide.core.api.ScalaProject
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.logging.HasLogger
import org.scalaide.core.resources.MarkerFactory
import scala.reflect.internal.Chars
import xsbti.Maybe

private case class SbtProblem(severity: Severity, message: String, position: Position, category: String) extends xsbti.Problem {
  override def equals(other: Any): Boolean = other match {
    case otherProblem: xsbti.Problem =>
      ((message == otherProblem.message)
        && (severity == otherProblem.severity)
        && m2o(position.offset) == m2o(otherProblem.position.offset)
        && m2o(position.line) == m2o(otherProblem.position.line)
        && m2o(position.sourceFile) == m2o(otherProblem.position.sourceFile))
    case _ => false
  }

  /** Simple hashcode to satisfy the equality implementation above */
  override def hashCode: Int =
    message.hashCode + severity.hashCode
}

/** An Sbt Reporter that creates error markers as build errors are reported.
 *
 *  @note It removes duplicate errors coming from scalac.
 */
private[zinc] class SbtBuildReporter(project: ScalaProject) extends xsbti.Reporter with HasLogger {
  private val probs = new mutable.ArrayBuffer[xsbti.Problem]
  private var seenErrors = false
  private var seenWarnings = false

  override def reset() = {
    seenErrors = false
    seenWarnings = false
    probs.clear()
  }

  override def hasErrors(): Boolean = seenErrors
  override def hasWarnings(): Boolean = seenWarnings
  override def printSummary(): Unit = {} //TODO
  override def problems: Array[xsbti.Problem] = probs.toArray
  override def comment(pos: xsbti.Position, msg: String): Unit = {}

  override def log(pos: Position, msg: String, sev: Severity) {
    val problem = SbtProblem(sev, msg, pos, "compile")
    if (!probs.contains(problem)) {
      createMarker(pos, msg, sev)
      probs += problem
    }

    import xsbti.Severity._
    sev match {
      case Warn  => seenWarnings = true
      case Error => seenErrors = true
      case _     =>
    }
  }

  def eclipseSeverity(severity: xsbti.Severity): Int = severity match {
    case xsbti.Severity.Info  => IMarker.SEVERITY_INFO
    case xsbti.Severity.Error => IMarker.SEVERITY_ERROR
    case xsbti.Severity.Warn  => IMarker.SEVERITY_WARNING
  }

  def createMarker(pos: Position, msg: String, sev: xsbti.Severity) = {
    import SbtUtils._
    val severity = eclipseSeverity(sev)

    val marker: Option[Unit] = for {
      file <- m2o(pos.sourceFile)
      resource <- FileUtils.resourceForPath(new Path(file.getAbsolutePath), project.underlying.getFullPath)
      offset <- m2o(pos.offset)
      line <- m2o(pos.line)
    } yield if (resource.getFileExtension != "java") {
      val markerPos = MarkerFactory.RegionPosition(offset, identifierLength(pos.lineContent, pos.pointer), line)
      BuildProblemMarker.create(resource, severity, msg, markerPos)
    } else
      logger.info("suppressed error in Java file: %s".format(msg))

    // if we couldn't determine what file/offset to put this marker on, create one on the project
    if (!marker.isDefined) {
      BuildProblemMarker.create(project.underlying, severity, msg)
    }
  }

  /** Return the identifier starting at `start` inside `content`. */
  private def identifierLength(content: String, start: Maybe[Integer]): Int = {
    def isOK(c: Char) = Chars.isIdentifierPart(c) || Chars.isOperatorPart(c)
    if (start.isDefined)
      (content drop start.get takeWhile isOK).size
    else
      0
  }
}

package org.scalaide.core.internal.builder.zinc

import scala.collection.mutable
import scala.reflect.internal.Chars

import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.Path
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.resources.MarkerFactory
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.internal.SbtUtils
import java.util.Optional

private case class SbtProblem(severity: xsbti.Severity, message: String, position: xsbti.Position, category: String)
    extends xsbti.Problem {

  import SbtUtils.toOption

  override def equals(other: Any): Boolean = other match {
    case otherProblem: xsbti.Problem =>
      ((message == otherProblem.message)
        && (severity == otherProblem.severity)
        && toOption(position.offset) == toOption(otherProblem.position.offset)
        && toOption(position.line) == toOption(otherProblem.position.line)
        && toOption(position.sourceFile) == toOption(otherProblem.position.sourceFile))
    case _ => false
  }

  /** Simple hashcode to satisfy the equality implementation above */
  override def hashCode: Int =
    message.hashCode + severity.hashCode
}

/**
 * An Sbt Reporter that creates error markers as build errors are reported.
 *
 * @note It removes duplicate errors coming from scalac.
 */
private[zinc] class SbtBuildReporter(project: IScalaProject) extends xsbti.Reporter with HasLogger {
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
  override def printSummary(): Unit = {} // TODO - implement this method
  override def problems: Array[xsbti.Problem] = probs.toArray
  override def comment(pos: xsbti.Position, msg: String): Unit = {}

  private def riseErrorOrWarning(sev: xsbti.Severity): Unit = {
    import xsbti.Severity._
    sev match {
      case Warn => seenWarnings = true
      case Error => seenErrors = true
      case _ =>
    }
  }

  private def riseNonJavaErrorOrWarning(pos: xsbti.Position, sev: xsbti.Severity): Unit =
    SbtUtils.toOption(pos.sourceFile).flatMap { file =>
      FileUtils.fileResourceForPath(new Path(file.getAbsolutePath), project.underlying.getFullPath)
    }.map { resource =>
      if (resource.getFileExtension != "java")
        riseErrorOrWarning(_)
      else
        (sev: xsbti.Severity) => ()
    }.orElse {
      Option(riseErrorOrWarning(_))
    }.foreach(_(sev))

  override def log(pos: xsbti.Position, msg: String, sev: xsbti.Severity): Unit = {
    val problem = SbtProblem(sev, msg, pos, "compile")
    if (!probs.contains(problem)) {
      createMarker(pos, msg, sev)
      probs += problem
    }
    riseNonJavaErrorOrWarning(pos, sev)
  }

  def eclipseSeverity(severity: xsbti.Severity): Int = severity match {
    case xsbti.Severity.Info => IMarker.SEVERITY_INFO
    case xsbti.Severity.Error => IMarker.SEVERITY_ERROR
    case xsbti.Severity.Warn => IMarker.SEVERITY_WARNING
  }

  def riseJavaErrorOrWarning(severity: Int) = severity match {
    case IMarker.SEVERITY_WARNING => riseErrorOrWarning(xsbti.Severity.Warn)
    case IMarker.SEVERITY_ERROR => riseErrorOrWarning(xsbti.Severity.Error)
    case _ =>
  }

  def createMarker(pos: xsbti.Position, msg: String, sev: xsbti.Severity) = {
    import SbtUtils._
    val severity = eclipseSeverity(sev)

    val marker: Option[Unit] = for {
      file <- toOption(pos.sourceFile)
      resource <- FileUtils.fileResourceForPath(new Path(file.getAbsolutePath), project.underlying.getFullPath)
      offset <- toOption(pos.offset)
      line <- toOption(pos.line)
    } yield if (resource.getFileExtension != "java") {
      val markerPos = MarkerFactory.RegionPosition(offset, identifierLength(pos.lineContent, pos.pointer), line)
      BuildProblemMarker.create(resource, severity, msg, markerPos)
    } else
      logger.info(s"suppressed error in Java file ${resource.getFullPath}:$line: $msg")

    // if we couldn't determine what file/offset to put this marker on, create one on the project
    if (!marker.isDefined) {
      BuildProblemMarker.create(project.underlying, severity, msg)
    }
  }

  /** Return the identifier starting at `start` inside `content`. */
  private def identifierLength(content: String, start: Optional[Integer]): Int = {
    def isOK(c: Char) = Chars.isIdentifierPart(c) || Chars.isOperatorPart(c)
    if (start.isPresent())
      (content drop start.get takeWhile isOK).size
    else
      0
  }
}

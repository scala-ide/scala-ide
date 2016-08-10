package org.scalaide.sbt.core.builder

import scala.reflect.internal.Chars

import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.Path
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.resources.MarkerFactory
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.FileUtils

import sbt.protocol.Position
import xsbti.Severity

/**
 * An Reporter that creates error markers as build errors are reported.
 */
class RemoteBuildReporter(project: IScalaProject) extends HasLogger {
  private def eclipseSeverity(severity: xsbti.Severity): Int = severity match {
    case xsbti.Severity.Info => IMarker.SEVERITY_INFO
    case xsbti.Severity.Error => IMarker.SEVERITY_ERROR
    case xsbti.Severity.Warn => IMarker.SEVERITY_WARNING
  }

  def createMarker(pos: Option[Position], msg: String, sev: Severity) = {
    val severity = eclipseSeverity(sev)

    pos.flatMap { pos =>
      for {
        file <- pos.sourceFile
        path <- pos.sourcePath
        resource <- FileUtils.resourceForPath(new Path(path), project.underlying.getFullPath)
        offset <- pos.offset
        line <- pos.line
      } yield if (resource.getFileExtension != "java") {
        val markerPos = MarkerFactory.RegionPosition(offset, identifierLength(pos.lineContent, pos.pointer), line)
        BuildProblemMarker.create(resource, severity, msg, markerPos)
      } else
        logger.error(s"suppressed error in Java file ${resource.getFullPath}:$line: $msg")
    }.orElse {
      Option(BuildProblemMarker.create(project.underlying, severity, msg))
    }
  }

  /** Return the identifier starting at `start` inside `content`. */
  private def identifierLength(content: String, start: Option[Int]): Int = {
    def isOK(c: Char) = Chars.isIdentifierPart(c) || Chars.isOperatorPart(c)
    if (start.isDefined)
      (content drop start.get takeWhile isOK).size
    else
      0
  }
}

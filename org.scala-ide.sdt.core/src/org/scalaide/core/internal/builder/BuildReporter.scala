package org.scalaide.core.internal.builder

import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.Reporter
import scala.reflect.internal.util.Position
import scala.reflect.internal.util.NoPosition
import org.scalaide.core.resources.EclipseResource
import org.scalaide.util.internal.eclipse.FileUtils
import org.scalaide.logging.HasLogger
import scala.collection.mutable.ListBuffer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IProgressMonitor
import org.scalaide.core.internal.project.ScalaProject

case class BuildProblem(severity: Reporter#Severity, msg: String, pos: Position)

abstract class BuildReporter(private[builder] val project0: ScalaProject, settings0: Settings) extends Reporter with HasLogger {
  val buildManager: EclipseBuildManager
  val prob: ListBuffer[BuildProblem] = ListBuffer.empty

  val taskScanner = new TaskScanner(project0)

  override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
    severity.count += 1
    if (severity.id > 1)
      buildManager.hasErrors = true

    // Filter out duplicates coming from the Scala compiler
    if (!prob.exists(p => p.pos == pos && p.msg == msg && p.severity == severity)) {
      val eclipseSeverity = severity.id match {
        case 2 => IMarker.SEVERITY_ERROR
        case 1 => IMarker.SEVERITY_WARNING
        case 0 => IMarker.SEVERITY_INFO
      }

      try {
        if(pos.isDefined) {
          pos.source.file match {
            case resource @ EclipseResource(i : IFile) =>
              if (!resource.hasExtension("java")) {
                BuildProblemMarker.create(i, eclipseSeverity, msg, pos)
                prob += new BuildProblem(severity, msg, pos)
              } else
                logger.info("suppressed error in Java file: %s".format(msg))
            case f =>
              logger.info("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
              EclipseResource.fromString(f.path, project0.underlying.getFullPath) match {
                case Some(i: IFile) =>
                  // this may happen if a file was compileLate by the build compiler
                  // for instance, when a source file (on the sourcepath) is newer than the classfile
                  // the compiler will create PlainFile instances in that case
                  prob += new BuildProblem(severity, msg, pos)
                  BuildProblemMarker.create(i, eclipseSeverity, msg, pos)
                case None =>
                  logger.info("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
                  prob += new BuildProblem(severity, msg, NoPosition)
                  BuildProblemMarker.create(project0.underlying, eclipseSeverity, msg)
              }
          }
        }
        else
          eclipseSeverity match {
            case IMarker.SEVERITY_INFO =>
              // print only to console, better debugging
                          logger.info("[info] " + msg)
            case _ =>
              prob += new BuildProblem(severity, msg, NoPosition)
              BuildProblemMarker.create(project0.underlying, eclipseSeverity, msg)
          }
      } catch {
        case ex : UnsupportedOperationException =>
          prob += new BuildProblem(severity, msg, NoPosition)
          BuildProblemMarker.create(project0.underlying, eclipseSeverity, msg)
      }
    }
  }

  override def comment(pos : Position, msg : String) {
    if (pos.isDefined) {
      val tasks = taskScanner.extractTasks(msg, pos)
      for (TaskScanner.Task(tag, msg, priority, pos) <- tasks if pos.isDefined) {
        val source = pos.source
        val start = pos.start
        val length = pos.end - start
        source.file match {
          case EclipseResource(i: IFile) =>
            FileUtils.task(i, tag, msg, priority, start, length, pos.line, null)
          case _ =>
        }
      }
    }
  }

  override def reset() {
    super.reset()
    prob.clear()
  }
}

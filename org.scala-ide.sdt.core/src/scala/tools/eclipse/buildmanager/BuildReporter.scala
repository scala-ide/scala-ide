package scala.tools.eclipse.buildmanager

import scala.tools.eclipse.{EclipseBuildManager, TaskScanner, ScalaProject}

import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.Position
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import scala.tools.eclipse.scalac_28.conversions._

import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.IProgressMonitor

abstract class BuildReporter(project0: ScalaProject, settings0: Settings) extends Reporter {
	val buildManager: EclipseBuildManager
  
  val taskScanner = new TaskScanner(project0)
  
  override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
    severity.count += 1
    if (severity.id > 1)
      buildManager.hasErrors = true
    
    val eclipseSeverity = severity.id match {
      case 2 => IMarker.SEVERITY_ERROR
      case 1 => IMarker.SEVERITY_WARNING
      case 0 => IMarker.SEVERITY_INFO
    }
    
    try {
      if(pos.isDefined) {
        val source = pos.source
        val length = source.identifier(pos).map(_.length).getOrElse(0)
        source.file match {
          case EclipseResource(i : IFile) => FileUtils.buildError(i, eclipseSeverity, msg, pos.point, length, pos.line, null)
          case f =>
            println("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
            EclipseResource.fromString(source.file.path) match {
              case Some(i: IFile) => 
                // this may happen if a file was compileLate by the build compiler
                // for instance, when a source file (on the sourcepath) is newer than the classfile
                // the compiler will create PlainFile instances in that case
                FileUtils.buildError(i, eclipseSeverity, msg, pos.point, length, pos.line, null)
              case _ =>
                println("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
                buildManager.buildError(eclipseSeverity, msg)
            }
        }
      }
      else
        eclipseSeverity match {
          case IMarker.SEVERITY_INFO if (settings0.Ybuildmanagerdebug.value) =>
	      	  // print only to console, better debugging
	      	  println("[Buildmanager info] " + msg)
          case _ =>
	      	  buildManager.buildError(eclipseSeverity, msg)   
        }
    } catch {
      case ex : UnsupportedOperationException => 
        buildManager.buildError(eclipseSeverity, msg)
    }
  }
  
  override def comment(pos : Position, msg : String) {
    val tasks = taskScanner.extractTasks(msg, pos)
    for (TaskScanner.Task(tag, msg, priority, pos) <- tasks if pos.isDefined) {
      val source = pos.source
      val start = pos.startOrPoint
      val length = pos.endOrPoint-start
      source.file match {
        case EclipseResource(i : IFile) =>
          FileUtils.task(i, tag, msg, priority, start, length, pos.line, null)
        case _ =>
      }
    }
  }
}
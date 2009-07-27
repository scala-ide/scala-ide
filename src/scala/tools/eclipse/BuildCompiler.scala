/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.Position
import scala.tools.nsc.reporters.Reporter

import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.IProgressMonitor

import scala.tools.eclipse.util.{ EclipseResource, FileUtils }

class BuildCompiler(val project : ScalaProject, settings : Settings) extends Global(settings) {

  reporter = new Reporter {
    override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
      severity.count += 1

      val eclipseSeverity = severity.id match {
        case 2 => IMarker.SEVERITY_ERROR
        case 1 => IMarker.SEVERITY_WARNING
        case 0 => IMarker.SEVERITY_INFO
      }
      
      (pos.offset, pos.source.map(_.file)) match {
        case (Some(offset), Some(file)) => 
          val source = pos.source.get
          val line = pos.line.get
          val length = source.identifier(pos, BuildCompiler.this).map(_.length).getOrElse(0)
          file match {
            case EclipseResource(i : IFile) => FileUtils.buildError(i, eclipseSeverity, msg, offset, length, line, null)
            case _ =>
          }
        case _ => 
          project.buildError(eclipseSeverity, msg, null)
      }
    }
  }
  
  def build(files : List[IFile], monitor : IProgressMonitor) = {
    val run = new Run {
      var worked = 0
      
      override def progress(current : Int, total : Int) : Unit = {
        if (monitor != null && monitor.isCanceled) {
          cancel
          return
        }
        
        val newWorked = if (current >= total) 100 else ((current.toDouble/total)*100).toInt
        if (worked < newWorked) {
          if (monitor != null)
            monitor.worked(newWorked-worked)
          worked = newWorked
        }
      }
    
      override def compileLate(file : AbstractFile) = {
        super.compileLate(file)
        
        file match {
          case EclipseResource(i : IFile) => FileUtils.clearBuildErrors(i, monitor)
          case _ => 
        }
      }
    }

    files.foreach(FileUtils.clearBuildErrors(_, monitor))
    project.createOutputFolders

    reporter.reset
    try {
      run.compileFiles(files.map(EclipseResource(_)))
    } catch {
      case ex =>
        ScalaPlugin.plugin.logError("Build compiler crashed", ex)
    }
    
    project.refreshOutput
  }
}

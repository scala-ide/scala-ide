/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.nio.charset._ 

import scala.collection.mutable.LinkedHashSet

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.Position
import scala.tools.nsc.reporters.Reporter

import org.eclipse.core.runtime.IProgressMonitor

class BuildCompiler(val project : ScalaProject) extends Global(new Settings) {
  val plugin = ScalaPlugin.plugin
  
  project.initialize(this)

  this.reporter = new Reporter {
    override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
      severity.count += 1
      (pos.offset, pos.source.map(_.file)) match {
        case (Some(offset), Some(file)) => 
          val source = pos.source.get
          project.buildError(file, severity.id, msg, offset, source.identifier(pos, BuildCompiler.this).getOrElse(" ").length)
        case _ => 
          project.buildError(severity.id, msg)
      }
    }
  }
  
  def build(files : List[AbstractFile], monitor : IProgressMonitor) = {
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
        project.clearBuildErrors(file)
      }
    }

    files.foreach(project.clearBuildErrors(_))

    reporter.reset
    try {
      run.compileFiles(files)
    } catch {
      case ex =>
        plugin.logError("Build compiler crashed", ex)
    }
    
    if (reporter.hasErrors)
      println("Has errors")

    project.refreshOutput
  }
}

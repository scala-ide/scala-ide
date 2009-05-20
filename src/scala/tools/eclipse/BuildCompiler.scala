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
  
  def build(toBuild : LinkedHashSet[AbstractFile], monitor : IProgressMonitor) : List[AbstractFile] = {
    // build all files, return what files have changed.
    val project = this.project
    val run = new Run {
      var worked : Int = 0
      override def progress(current : Int, total : Int) : Unit = {
        if (monitor != null && monitor.isCanceled) {
          cancel; return
        }
        assert(current <= total)
        val expected = (current:Double) / (total:Double)
        val worked0 = (expected * 100f).toInt
        assert(worked0 <= 100)
        if (worked0 > worked) {
          if (monitor != null) monitor.worked(worked0 - worked)
          worked = worked0
        }
      }
      override def compileLate(pfile : AbstractFile) = {
        super.compileLate(pfile)
        if (toBuild put pfile) {
          Console.println("late " + pfile)
          project.clearBuildErrors(pfile)
        }
      }
    }
    //val plugin = this.plugin
    val files = toBuild.toList
    project.clearBuildErrors()
    reporter.reset
    try {
      run.compileFiles(files)
    } catch  {
      case e =>
        plugin.logError("Build compiler (scalac) crashed", e)
        return Nil
    } finally {
      ()
    }
    project.refreshOutput
    Nil
  }
}

/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import java.nio.charset._ 

import scala.collection.mutable.LinkedHashSet

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.{ AbstractFile, PlainFile }
import scala.tools.nsc.util.Position
import scala.tools.nsc.reporters.Reporter

import org.eclipse.core.runtime.IProgressMonitor

class BuildCompiler(val project : CompilerProject) extends Global(new Settings) {
  val plugin = ScalaPlugin.plugin
  
  project.initialize(this)

  this.reporter = new Reporter {
    override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = (pos.offset,pos.source.map(_.file)) match {
    case (Some(offset),Some(file:PlainFile)) => 
      val source = pos.source.get
      //import IMarker._
      val project = BuildCompiler.this.project
      severity.count += 1
      file
      project.buildError(file, severity.id, msg, offset, source.identifier(pos, BuildCompiler.this).getOrElse(" ").length)
    case _ => 
      severity.count += 1
      pos match {
        case _ =>
          project.buildError(severity.id, msg)
      }
      //assert(false)
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
        //val file = project.nscToLampion(pfile.asInstanceOf[PlainFile]).asInstanceOf[File]
        if (toBuild put pfile) {
          Console.println("late " + pfile)
          project.clearBuildErrors(pfile) // .clearBuildErrors
        }
      }
    }
    //val plugin = this.plugin
    val filenames = toBuild.map(_.file.getAbsolutePath).toList
    reporter.reset
    project.clearBuildErrors()
    try {
      run.compile(filenames)
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

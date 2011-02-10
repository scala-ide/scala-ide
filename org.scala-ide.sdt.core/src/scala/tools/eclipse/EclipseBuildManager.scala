/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.util.Tracer
import org.eclipse.core.resources.{ IResource, IFile, IMarker, IWorkspaceRunnable }
import org.eclipse.core.runtime.IProgressMonitor
import scala.collection.mutable.HashSet
import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.interactive.RefinedBuildManager
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.util.Position
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.util.IDESettings

class EclipseBuildManager(project : ScalaProject, settings0: Settings) extends RefinedBuildManager(settings0) {
  private var monitor : IProgressMonitor = new NullProgressMonitor()
  private lazy val pendingSources = new HashSet[IFile] ++ project.allSourceFiles() // first run should build every sources
  
//  private val depFile = {
//    val b = project.underlying.getFile(".scala_dependencies")
//    b.delete(true, false, null/*monitor*/)
//    b
//  }
      
  
  class EclipseBuildCompiler(settings : Settings, reporter : Reporter) extends BuilderGlobal(settings, reporter) {

    def buildReporter = reporter.asInstanceOf[BuildReporter]
    
    buildReporter.compiler = this
    
    override def newRun() =
      new Run {
        var worked = 0
        
        override def progress(current : Int, total : Int) : Unit = {
          if (monitor.isCanceled) {
            cancel
            return
          }
          
          val newWorked = if (current >= total) 100 else ((current.toDouble/total)*100).toInt
          if (worked < newWorked) {
            monitor.worked(newWorked-worked)
            worked = newWorked
          }
        }
      
        override def compileLate(file : AbstractFile) = {
          file match {
            case EclipseResource(i : IFile) =>
              pendingSources += i
              FileUtils.clearBuildErrors(i, monitor)
              FileUtils.clearTasks(i, monitor)
            case _ => 
          }
          super.compileLate(file)
        }
      }
  }

  class BuildReporter(project : ScalaProject) extends Reporter {
    var compiler : Global = _
    
    val taskScanner = new TaskScanner(project)
    
    override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
      severity.count += 1
      
      val eclipseSeverity = severity.id match {
        case 2 => IMarker.SEVERITY_ERROR
        case 1 => IMarker.SEVERITY_WARNING
        case 0 => IMarker.SEVERITY_INFO
      }
      
      def notifyBuildError(file : IFile, severity : Int, msg : String, offset : Int, length : Int, line : Int, monitor : IProgressMonitor) {
        FileUtils.buildError(file, severity, msg, offset, length, line, monitor)
      }
      try {
        if(pos.isDefined) {
          val source = pos.source
          val length = source.identifier(pos, compiler).map(_.length).getOrElse(0)
          source.file match {
            case EclipseResource(i : IFile) => buildError(i, eclipseSeverity, msg, pos.point, length, pos.line)
            case f =>
              Tracer.println("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
              EclipseResource.fromString(source.file.path) match {
                case Some(i: IFile) => 
                  // this may happen if a file was compileLate by the build compiler
                  // for instance, when a source file (on the sourcepath) is newer than the classfile
                  // the compiler will create PlainFile instances in that case
                  buildError(i, eclipseSeverity, msg, pos.point, length, pos.line)
                case None =>
                  Tracer.println("no EclipseResource associated to %s [%s]".format(f.path, f.getClass))
                  buildError(eclipseSeverity, msg)
              }
          }
        }
        else
          eclipseSeverity match {
            case IMarker.SEVERITY_INFO if (settings0.Ybuildmanagerdebug.value) =>
        	  // print only to console, better debugging
        	  println("[Buildmanager info] " + msg)
            case _ =>
        	  buildError(eclipseSeverity, msg)   
          }
      } catch {
        case ex : UnsupportedOperationException => 
          buildError(eclipseSeverity, msg)
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

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile], pm : IProgressMonitor) {
    monitor = if (pm == null) new NullProgressMonitor() else pm
    monitor.beginTask("build scala files", 100)       
    val pendingSources0 = pendingSources ++ addedOrUpdated
    pendingSources.clear
    val removedFiles = removed.map(EclipseResource(_) : AbstractFile)
    val unbuilt0 = unbuilt
    val toBuild = pendingSources0.map(EclipseResource(_)) ++ unbuilt0 -- removedFiles
    Tracer.printlnItems("ask to build (addedOrUpdated)  ", addedOrUpdated)
    Tracer.printlnItems("ask to build (pendingSources)  ", pendingSources)
    Tracer.printlnItems("ask to build (unbuilt)         ", unbuilt0)
    Tracer.printlnItems("ask to build (removed)         ", removedFiles)
    var hasErrors = false
    clearBuildErrors(monitor)
    try {
      super.update(toBuild, removedFiles)
      hasErrors = compiler.reporter.ERROR.count > 0
    } catch {
      case e =>
        hasErrors = true
        buildError(IMarker.SEVERITY_ERROR, "Error in Scala compiler: " + e.getMessage)
        ScalaPlugin.plugin.logError("Error in Scala compiler", e)
        //project.resetBuildCompiler(pm)
    }
    Tracer.println("build ends with %d error(s), %d warnings".format(compiler.reporter.ERROR.count, compiler.reporter.WARNING.count))
    if (hasErrors)
      pendingSources ++= pendingSources0
      
//    saveTo(EclipseResource(depFile), _.toString)
//    depFile.setDerived(true)
//    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)      
  }
  
  def unbuilt : Set[AbstractFile] = {
    val targets = compiler.dependencyAnalysis.dependencies.targets
    val missing = new HashSet[AbstractFile]
    for (src <- targets.keysIterator)
      if (targets(src).exists(!_.exists))
        missing += src
    
    missing.toSet
  }
  
  /** ignore settings parameter and used settings from constructor */
  override def newCompiler(settings: Settings) = new EclipseBuildCompiler(settings0, new BuildReporter(project))
  
  override def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    for(file <- included) {
      Tracer.println("buildingFile : " + file)
      file match {
        case EclipseResource(f : IFile) =>
          FileUtils.clearBuildErrors(f, null)
          FileUtils.clearTasks(f, null)
        case _ =>
      }
    }
  }
  
    private def runOnWorker(monitor : IProgressMonitor)(f : => Unit) {
    Thread.currentThread.getName.startsWith("Worker-") match {
      case true => f
      case false => {
        project.underlying.getWorkspace.run(new IWorkspaceRunnable {
          def run(monitor : IProgressMonitor) = { f }
        }, monitor)
      }
    }
  }
  
  protected def buildError(severity : Int, msg : String) = runOnWorker(monitor){
        val mrk = project.underlying.createMarker(ScalaPlugin.plugin.problemMarkerId)
        mrk.setAttribute(IMarker.SEVERITY, severity)
        val string = msg.map{
          case '\n' => ' '
          case '\r' => ' '
          case c => c
        }.mkString("","","")
        mrk.setAttribute(IMarker.MESSAGE , msg)
  }

  protected def buildError(file : IFile, severity : Int, msg : String, offset : Int, length : Int, line : Int) {
    if (IDESettings.ignoreErrorOnJavaFile.value && file.getFileExtension == "java") {
      return
    }
    FileUtils.buildError(file, severity, msg, offset, length, line, monitor)
  }  

  def clearBuildErrors(monitor : IProgressMonitor) = runOnWorker(monitor){
    project.underlying.deleteMarkers(ScalaPlugin.plugin.problemMarkerId, true, IResource.DEPTH_ZERO)
  }
}

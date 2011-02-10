package scala.tools.eclipse
package buildmanager
package refined

import org.eclipse.core.resources.{ IFile, IMarker, IResource }
import org.eclipse.core.runtime.IProgressMonitor

import scala.collection.mutable.HashSet

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.interactive.{BuildManager, RefinedBuildManager}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter

import scala.tools.eclipse.util.{ EclipseResource, FileUtils }

class EclipseRefinedBuildManager(project : ScalaProject, settings0: Settings)
  extends RefinedBuildManager(settings0) with EclipseBuildManager {
  var depFile:IFile = project.underlying.getFile(".scala_dependencies")
  var monitor : IProgressMonitor = _
  val pendingSources = new HashSet[IFile]
  
  class EclipseBuildCompiler(settings : Settings, reporter : Reporter) extends BuilderGlobal(settings, reporter) {
    
    override def newRun() =
      new Run {
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

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile])(implicit pm : IProgressMonitor) {
    monitor = pm
    
    pendingSources ++= addedOrUpdated
    val removedFiles = removed.map(EclipseResource(_) : AbstractFile)
    val toBuild = pendingSources.map(EclipseResource(_)) ++ unbuilt -- removedFiles
    hasErrors = false
    try {
      super.update(toBuild, removedFiles)
    } catch {
      case e =>
        hasErrors = true
        project.buildError(IMarker.SEVERITY_ERROR, "Error in Scala compiler: " + e.getMessage, null)
        ScalaPlugin.plugin.logError("Error in Scala compiler", e)
    }
    if (!hasErrors)
      pendingSources.clear
      
    saveTo(EclipseResource(depFile), _.toString)
    depFile.setDerived(true)
    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)
  }
  
  private def unbuilt : Set[AbstractFile] = {
    val targets = compiler.dependencyAnalysis.dependencies.targets
    val missing = new HashSet[AbstractFile]
    for (src <- targets.keysIterator)
      if (targets(src).exists(!_.exists))
        missing += src
    
    Set.empty ++ missing
  }
  
  override def newCompiler(settings: Settings) = new EclipseBuildCompiler(settings,
  		new BuildReporter(project, settings) {
  	    val buildManager = EclipseRefinedBuildManager.this
      })
  
  override def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    for(file <- included) {
      file match {
        case EclipseResource(f : IFile) =>
          FileUtils.clearBuildErrors(f, null)
          FileUtils.clearTasks(f, null)
        case _ =>
      }
    }
  }
  
  def clean(implicit monitor: IProgressMonitor) {
  	depFile.delete(true, false, monitor)
  }

  // pre: project hasn't been built
  def invalidateAfterLoad: Boolean = {
  	if (!depFile.exists())
        true
      else {
        try {
          !loadFrom(EclipseResource(depFile), EclipseResource.fromString(_).getOrElse(null))
        } catch { case _ => true }
      }
  }
}

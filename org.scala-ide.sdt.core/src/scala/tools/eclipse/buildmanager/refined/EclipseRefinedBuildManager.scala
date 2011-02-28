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
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.util.{ EclipseResource, FileUtils, Defensive, Tracer}

class EclipseRefinedBuildManager(val project : ScalaProject, settings0: Settings) extends RefinedBuildManager(settings0) with EclipseBuildManager {
  var depFile:IFile = project.underlying.getFile(".scala_dependencies")
  private var monitor : IProgressMonitor = new NullProgressMonitor()
  private lazy val pendingSources = new HashSet[IFile] ++ project.allSourceFiles() // first run should build every sources
  
  class EclipseBuildCompiler(settings : Settings, reporter : Reporter) extends BuilderGlobal(settings, reporter) {
    
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
          if (Defensive.notNull(file, "try to compile file == null")) {
            super.compileLate(file)
          }
        }
      }

  }

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile])(implicit pm : IProgressMonitor) {
    monitor = if (pm == null) new NullProgressMonitor() else pm
    
    pendingSources ++= addedOrUpdated

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
    }
    Tracer.println("build ends with %d error(s), %d warnings".format(compiler.reporter.ERROR.count, compiler.reporter.WARNING.count))
    if (hasErrors)
      pendingSources ++= pendingSources0
      
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
    
    missing.toSet
  }
  
  override def newCompiler(settings: Settings) = new EclipseBuildCompiler(settings,
		new BuildReporter(project, settings) {
	    val buildManager = EclipseRefinedBuildManager.this
    }
  )
  
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

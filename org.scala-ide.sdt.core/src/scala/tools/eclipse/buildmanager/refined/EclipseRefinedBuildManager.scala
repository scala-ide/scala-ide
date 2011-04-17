package scala.tools.eclipse
package buildmanager
package refined

import org.eclipse.core.resources.{ IFile, IMarker, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import scala.collection.mutable.HashSet
import scala.tools.nsc.{ Global, Settings, Phase }
import scala.tools.nsc.interactive.{ BuildManager, RefinedBuildManager }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.util.{ EclipseResource, FileUtils, Defensive, Tracer}

import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import org.eclipse.core.runtime.{ SubMonitor, IPath, Path }

class EclipseRefinedBuildManager(val project: ScalaProject, settings0: Settings) extends RefinedBuildManager(settings0) with EclipseBuildManager {
  var depFile:IFile = project.underlying.getFile(".scala_dependencies")
  var monitor: SubMonitor = _
  private lazy val pendingSources = new HashSet[IFile] ++ project.allSourceFiles() // first run should build every sources
  val projectPath = project.javaProject.getProject.getLocation
  
  class EclipseBuildCompiler(settings : Settings, reporter : Reporter) extends BuilderGlobal(settings, reporter) {
    
    override def newRun() =
      new Run {
        var lastWorked = 0
        var savedTotal = 0
        
//BACK-2.8        
//        override def informUnitStarting(phase: Phase, unit: CompilationUnit) {
//          val unitPath: IPath = Path.fromOSString(unit.source.path)
//          monitor.subTask("phase " + phase.name + " for " + unitPath.makeRelativeTo(projectPath))
//        }

        override def progress(current: Int, total: Int) {
          if (monitor.isCanceled) {
            cancel
            return
          }

          if (savedTotal != total) {
            monitor.setWorkRemaining(total - savedTotal)
            savedTotal = total
          }

          if (lastWorked < current) {
            monitor.worked(current - lastWorked)
            lastWorked = current
          }
        }

        // TODO: check to make sure progress monitor use is correct
        override def compileLate(file: AbstractFile) = {
          file match {
            case EclipseResource(i: IFile) =>
              pendingSources += i
              FileUtils.clearBuildErrors(i, monitor.newChild(1))
              FileUtils.clearTasks(i, monitor.newChild(1))
            case _ =>
          }
          if (Defensive.notNull(file, "try to compile file == null")) {
            super.compileLate(file)
          }
        }
      }

  }

  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], submon: SubMonitor) {
    monitor = submon

    pendingSources ++= addedOrUpdated
    val pendingSources0 = pendingSources ++ addedOrUpdated
    pendingSources.clear
    val removedFiles = removed.map(EclipseResource(_): AbstractFile)
    val unbuilt0 = unbuilt
    val toBuild = pendingSources0.map(EclipseResource(_)) ++ unbuilt0 -- removedFiles
    Tracer.printlnItems("ask to build (addedOrUpdated)  ", addedOrUpdated)
    Tracer.printlnItems("ask to build (pendingSources)  ", pendingSources)
    Tracer.printlnItems("ask to build (unbuilt)         ", unbuilt0)
    Tracer.printlnItems("ask to build (removed)         ", removedFiles)
    var hasErrors = false
    //clearBuildErrors(monitor)
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
      
    ensureDepFileFresh()
    saveTo(EclipseResource(depFile), _.toString)
    //BACK-e35
    //depFile.setDerived(true, monitor)
    depFile.setDerived(true)
    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)

    val targets = compiler.dependencyAnalysis.dependencies.targets
    toBuild flatMap targets foreach {
      case EclipseResource(f) =>
        //BACK-e35
        //f.setDerived(true, monitor)
        f.setDerived(true)
      case _ =>
    }
  }

  // TODO: this is a temporary fix for a bug where writing to dependencies file isn't performed
  // looks to me like underlying stream isn't created/closed properly
  private def ensureDepFileFresh() {
    depFile.delete(true, null)
    depFile = project.underlying.getFile(".scala_dependencies")
  }

  private def unbuilt: Set[AbstractFile] = {
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
    })

  override def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    for (file <- included) {
      file match {
        case EclipseResource(f: IFile) =>
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

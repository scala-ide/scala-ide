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
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import org.eclipse.core.runtime.{ SubMonitor, IPath, Path }
import scala.tools.eclipse.util.HasLogger

class EclipseRefinedBuildManager(project: ScalaProject, settings0: Settings)
  extends RefinedBuildManager(settings0) with EclipseBuildManager with HasLogger {
  var depFile: IFile = project.underlying.getFile(".scala_dependencies")
  var monitor: SubMonitor = _
  val pendingSources = new HashSet[IFile]
  val projectPath = project.javaProject.getProject.getLocation

  class EclipseBuildCompiler(settings: Settings, reporter: Reporter) extends BuilderGlobal(settings, reporter) {

    def hasBuildErrors = 
      reporter.hasErrors
    
    override def newRun() =
      new Run {
        var lastWorked = 0
        var savedTotal = 0
        var throttledMessages = 0

        override def informUnitStarting(phase: Phase, unit: CompilationUnit) {
          throttledMessages += 1
          if (throttledMessages == 10) {
            throttledMessages = 0
            val unitPath: IPath = Path.fromOSString(unit.source.path)
            monitor.subTask("phase " + phase.name + " for " + unitPath.makeRelativeTo(projectPath))
          }
        }

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
          super.compileLate(file)
        }
      }

  }
  
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], submon: SubMonitor) {
    monitor = submon

    pendingSources ++= addedOrUpdated
    val removedFiles = removed.map(EclipseResource(_): AbstractFile)
    val toBuild = pendingSources.map(EclipseResource(_)) ++ unbuilt -- removedFiles
    hasErrors = false
    try {
      super.update(toBuild, removedFiles)
    } catch {
      case e =>
        hasErrors = true
        project.buildError(IMarker.SEVERITY_ERROR, "Error in Scala compiler: " + e.getMessage, null)
        logger.error("Error in Scala compiler", e)
    }
    if (!hasErrors)
      pendingSources.clear

    ensureDepFileFresh()
    saveTo(EclipseResource(depFile), _.toString)
    depFile.setDerived(true, monitor)
    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)

    hasBuildErrors = compiler.reporter.hasErrors
    val targets = compiler.dependencyAnalysis.dependencies.targets
    toBuild flatMap targets foreach {
      case EclipseResource(f) =>
        f.setDerived(true, null) // don't report progress on setting derived attributes, as the UI is slower than the operation
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

    Set.empty ++ missing
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

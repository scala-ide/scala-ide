package scala.tools.eclipse
package buildmanager
package sbtintegration

import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.io.AbstractFile
import scala.reflect.internal.util.NoPosition
import scala.collection.mutable
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.runtime.Path
import java.io.File
import xsbti.compile.CompileProgress
import xsbti.Logger
import xsbti.F0
import sbt.Process
import sbt.ClasspathOptions
import scala.tools.eclipse.util.EclipseResource
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.properties.ScalaPluginSettings
import org.eclipse.core.resources.IResource
import scala.tools.eclipse.logging.HasLogger
import sbt.inc.AnalysisStore
import sbt.inc.Analysis
import sbt.inc.FileBasedStore
import sbt.inc.Incremental
import sbt.compiler.IC
import sbt.compiler.CompileFailed
import org.eclipse.core.resources.IProject
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReference

/** An Eclipse builder using the Sbt engine.
 *
 *  Unlike the command line Sbt, this builder always instantiates the
 *  compiler that is shipped with Eclipse. This is by-design, since our Sbt engine
 *  should not download any artifacts (other versions of the compiler), therefore
 *  it can only use the one deployed inside Eclipse. This can be improved in the future.
 *
 *  The classpath is handled by delegating to the underlying project. That means
 *  a valid Scala library has to exist on the classpath, but it's not limited to
 *  being called 'scala-library.jar': @see [[scala.tools.eclipse.ClasspathManagement]] for
 *  how the library is resolved (it can be any jar or even an existing dependent project).
 */
class EclipseSbtBuildManager(val project: ScalaProject, settings0: Settings)
  extends EclipseBuildManager with HasLogger {

  private var monitor: SubMonitor = _

  private class SbtProgress extends CompileProgress {
    private var lastWorked = 0
    private var savedTotal = 0
    private var throttledMessages = 0

    override def startUnit(phaseName: String, unitPath: String) {
      def unitIPath: IPath = Path.fromOSString(unitPath)

      // dirty-hack for ticket #1001595 until Sbt provides a better API for tracking sources recompiled by the incremental compiler
      if(phaseName == "parser") FileUtils.toIFile(unitIPath).foreach(clearMarkers)

      // What follows is a direct copy of the mechanism in the refined build managers
      throttledMessages += 1
      if (throttledMessages == 10) {
        throttledMessages = 0
        val projectPath = project.javaProject.getProject.getLocation
        monitor.subTask("phase " + phaseName + " for " + unitIPath.makeRelativeTo(projectPath))
      }
    }

    override def advance(current: Int, total: Int): Boolean =
       if (monitor.isCanceled) {
        false
      } else {
        if (savedTotal != total) {
          monitor.setWorkRemaining(total - savedTotal)
          savedTotal = total
        }

        if (lastWorked < current) {
          monitor.worked(current - lastWorked)
          lastWorked = current
        }
        true
      }
  }

  private val pendingSources = new mutable.HashSet[IFile]

  private val sbtLogger = new xsbti.Logger {
    override def error(msg: F0[String]) = logger.error(msg())
    override def warn(msg: F0[String]) = logger.warn(msg())
    override def info(msg: F0[String]) = logger.info(msg())
    override def debug(msg: F0[String]) = logger.debug(msg())
    override def trace(exc: F0[Throwable]) = logger.error("", exc())
  }

  private val buildReporter = new BuildReporter(project, settings0) {
    val buildManager = EclipseSbtBuildManager.this
  }
  private lazy val sbtReporter: xsbti.Reporter = new SbtBuildReporter(buildReporter)

  private implicit class FileHelper(files: scala.collection.Set[AbstractFile]) {
    def asJFiles: Seq[File] = files.map(_.file).toSeq
  }


  private val sources: mutable.Set[AbstractFile] = mutable.Set.empty

  /** Remove the given files from the managed build process. */
  private def removeFiles(files: scala.collection.Set[AbstractFile]) {
    if (!files.isEmpty)
      sources --= files
  }

  /** The given files have been modified by the user. Recompile
   *  them and their dependent files.
   */
  private def update(added: scala.collection.Set[AbstractFile], removed: scala.collection.Set[AbstractFile]) {
    if (added.isEmpty && removed.isEmpty)
      logger.info("No changes in project, running the builder for potential transitive changes.")

    buildingFiles(added)
    removeFiles(removed)
    sources ++= added
    runCompiler(sources.asJFiles)
  }

  private def runCompiler(sources: Seq[File]) {
    val inputs = new SbtInputs(sources.toSeq, project, monitor, new SbtProgress, cacheFile, sbtReporter, sbtLogger)
    val analysis =
      try
        Some(IC.compile(inputs, sbtLogger))
      catch {
        case _: CompileFailed => None
      }
    analysis foreach setCached
  }

  private val cached = new AtomicReference[SoftReference[Analysis]]
  private def setCached(a: Analysis): Analysis = {
   cached set new SoftReference[Analysis](a); a
  }
  private[sbtintegration] def latestAnalysis: Analysis = Option(cached.get) flatMap (ref => Option(ref.get)) getOrElse setCached(IC.readAnalysis(cacheFile))

  private val cachePath = project.underlying.getFile(".cache")
  private def cacheFile = cachePath.getLocation.toFile


  override var depFile: IFile = null

  override def clean(implicit monitor: IProgressMonitor) {
    cachePath.refreshLocal(IResource.DEPTH_ZERO, null)
    cachePath.delete(true, false, monitor)
    // refresh explorer
  }
  override def invalidateAfterLoad: Boolean = true


  private def unbuilt: Set[AbstractFile] = Set.empty // TODO: this should be taken care of

  override def build(addedOrUpdated : Set[IFile], removed : Set[IFile], pm: SubMonitor) {
    buildReporter.reset()
    pendingSources ++= addedOrUpdated
    val removedFiles = removed.map(EclipseResource(_) : AbstractFile)
    val toBuild = pendingSources.map(EclipseResource(_)) ++ unbuilt -- removedFiles
    monitor = pm
    hasErrors = false
    try {
      update(toBuild, removedFiles)
    } catch {
      case e: Throwable =>
        hasErrors = true
        BuildProblemMarker.create(project, e)
        eclipseLog.error("Error in Scala compiler", e)
        buildReporter.error(NoPosition, "SBT builder crashed while compiling. The error message is '" + e.getMessage() + "'. Check Error Log for details.")
    }

    hasErrors = sbtReporter.hasErrors || hasErrors
    if (!hasErrors)
      pendingSources.clear
  }

  private def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    included foreach {
      case EclipseResource(f : IFile) => clearMarkers(f)
      case _ =>
    }
  }

  private def clearMarkers(f: IFile): Unit = {
    FileUtils.clearBuildErrors(f, null)
    FileUtils.clearTasks(f, null)
  }
}

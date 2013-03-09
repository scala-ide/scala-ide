package scala.tools.eclipse
package buildmanager
package sbtintegration

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.NoPosition
import scala.collection.mutable
import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.{ IProgressMonitor, IPath, SubMonitor, Path}
import java.io.File
import xsbti.compile.CompileProgress
import xsbti.{ Logger, F0 }
import sbt.{Process, ClasspathOptions}
import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import scala.tools.eclipse.properties.ScalaPluginSettings
import org.eclipse.core.resources.IResource
import scala.tools.eclipse.logging.HasLogger
import sbt.inc.{ AnalysisStore, Analysis, FileBasedStore, Incremental }
import sbt.compiler.{ IC, CompileFailed }
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

  var monitor: SubMonitor = _

  class SbtProgress extends CompileProgress {
    private var lastWorked = 0
    private var savedTotal = 0
    private var throttledMessages = 0

    // Direct copy of the mechanism in the refined build managers
    def startUnit(phaseName: String, unitPath: String) {
      throttledMessages += 1
      if (throttledMessages == 10) {
        throttledMessages = 0
        val unitIPath: IPath = Path.fromOSString(unitPath)
        val projectPath = project.javaProject.getProject.getLocation
        monitor.subTask("phase " + phaseName + " for " + unitIPath.makeRelativeTo(projectPath))
      }
    }

    def advance(current: Int, total: Int): Boolean =
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

  val pendingSources = new mutable.HashSet[IFile]

  val sbtLogger = new xsbti.Logger {
    def error(msg: F0[String]) = logger.error(msg())
    def warn(msg: F0[String]) = logger.warn(msg())
    def info(msg: F0[String]) = logger.info(msg())
    def debug(msg: F0[String]) = logger.debug(msg())
    def trace(exc: F0[Throwable]) = logger.error("", exc())
  }

  val buildReporter = new BuildReporter(project, settings0) {
    val buildManager = EclipseSbtBuildManager.this
  }
  lazy val sbtReporter: xsbti.Reporter = new SbtBuildReporter(buildReporter)

  implicit def toFile(files: mutable.Set[AbstractFile]): Seq[File] = files.map(_.file).toSeq
  implicit def toFile(files: scala.collection.Set[AbstractFile]): Seq[File] = files.map(_.file).toSeq


  private val sources: mutable.Set[AbstractFile] = mutable.Set.empty

  /** Add the given source files to the managed build process. */
  def addSourceFiles(files: scala.collection.Set[AbstractFile]) {
    if (!files.isEmpty) {
      sources ++= files
      runCompiler(sources)
    }
  }

  /** Remove the given files from the managed build process. */
  def removeFiles(files: scala.collection.Set[AbstractFile]) {
    if (!files.isEmpty)
      sources --= files
  }

  /** The given files have been modified by the user. Recompile
   *  them and their dependent files.
   */
  def update(added: scala.collection.Set[AbstractFile], removed: scala.collection.Set[AbstractFile]) {
    logger.info("update files: " + added)
    if (!added.isEmpty || !removed.isEmpty) {
      buildingFiles(added)
      removeFiles(removed)
      sources ++= added
      runCompiler(sources)
    }
  }

  private def runCompiler(sources: Seq[File]) {
    System.setProperty(Incremental.incDebugProp,
      project.storage.getString(SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.debugIncremental.name)))
    val inputs = new SbtInputs(sources.toSeq, project, monitor, new SbtProgress, cacheFile, sbtReporter, sbtLogger)
    val analysis =
      try
        Some(IC.compile(inputs, sbtLogger))
      catch {
        case _: CompileFailed => None
      }
    analysis foreach setCached
  }

  val cached = new AtomicReference[SoftReference[Analysis]]
  def setCached(a: Analysis): Analysis = {
   cached set new SoftReference[Analysis](a); a
  }
  def latestAnalysis: Analysis = Option(cached.get) flatMap (ref => Option(ref.get)) getOrElse setCached(IC.readAnalysis(cacheFile))

  val cachePath = project.underlying.getFile(".cache")
  def cacheFile = cachePath.getLocation.toFile


  /** Not supported */
  def loadFrom(file: AbstractFile, toFile: String => AbstractFile) : Boolean = true

  /** Not supported */
  def saveTo(file: AbstractFile, fromFile: AbstractFile => String) {}

  /** Not supported */
  val compiler: Global = null
  var depFile: IFile = null

  def clean(implicit monitor: IProgressMonitor) {
    cachePath.refreshLocal(IResource.DEPTH_ZERO, null)
    cachePath.delete(true, false, monitor)
    // refresh explorer
  }
  def invalidateAfterLoad: Boolean = true


  private def unbuilt: Set[AbstractFile] = Set.empty // TODO: this should be taken care of

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile], pm: SubMonitor) {
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
}

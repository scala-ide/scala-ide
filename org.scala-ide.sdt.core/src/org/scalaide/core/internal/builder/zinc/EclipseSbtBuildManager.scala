package org.scalaide.core.internal.builder
package zinc

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
import org.scalaide.core.resources.EclipseResource
import org.scalaide.util.internal.eclipse.FileUtils
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.eclipse.core.resources.IResource
import org.scalaide.logging.HasLogger
import sbt.inc.AnalysisStore
import sbt.inc.Analysis
import sbt.inc.FileBasedStore
import sbt.inc.Incremental
import sbt.inc.IncOptions
import sbt.compiler.IC
import sbt.compiler.CompileFailed
import org.eclipse.core.resources.IProject
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReference
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.internal.builder.EclipseBuildManager
import xsbti.compile.Inputs
import sbt.compiler.AnalyzingCompiler
import sbt.compiler.AggressiveCompile
import sbt.inc.IncOptions
import xsbti.Maybe
import org.scalaide.util.internal.SbtUtils.m2o
import org.scalaide.core.ScalaPlugin
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.project.ScalaInstallation
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import scala.util.hashing.Hashing

/** An Eclipse builder using the Sbt engine.
 *
 *  Unlike the command line Sbt, this builder always instantiates the
 *  compiler that is shipped with Eclipse. This is by-design, since our Sbt engine
 *  should not download any artifacts (other versions of the compiler), therefore
 *  it can only use the one deployed inside Eclipse. This can be improved in the future.
 *
 *  The classpath is handled by delegating to the underlying project. That means
 *  a valid Scala library has to exist on the classpath, but it's not limited to
 *  being called 'scala-library.jar': @see [[org.scalaide.core.internal.project.ClasspathManagement]] for
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
      if (phaseName == "parser") FileUtils.toIFile(unitIPath).foreach(FileUtils.clearTasks(_, null))

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

    project.underlying.deleteMarkers(ScalaPlugin.plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    buildingFiles(added)
    removeFiles(removed)
    sources ++= added
    runCompiler(sources.asJFiles)
  }

  private def runCompiler(sources: Seq[File]) {
    val scalaInstall = findInstallation(project)
    logger.info(s"Running compiler using $scalaInstall")
    val inputs = new SbtInputs(scalaInstall, sources, project, monitor, new SbtProgress, tempDirFile, sbtLogger)
    val analysis =
      try
        Some(aggressiveCompile(inputs, sbtLogger))
      catch {
        case _: CompileFailed | CompilerInterfaceFailed => None
      }
    analysis foreach setCached
  }

  private val cached = new AtomicReference[SoftReference[Analysis]]
  private def setCached(a: Analysis): Analysis = {
    cached set new SoftReference[Analysis](a); a
  }
  private def clearCached(): Unit = {
    Option(cached.get) foreach (ref => ref.clear)
  }
  // take by-name argument because we need incOptions only when we have a cache miss
  private[zinc] def latestAnalysis(incOptions: => IncOptions): Analysis =
    Option(cached.get) flatMap (ref => Option(ref.get)) getOrElse setCached(IC.readAnalysis(cacheFile, incOptions))

  private val cachePath = project.underlying.getFile(".cache")
  private def cacheFile = cachePath.getLocation.toFile

  // this directory is used by Sbt to store classfiles between
  // compilation runs to implement all-or-nothing compilation
  // sementics. Original files are copied over to tempDir and
  // moved back in case of compilation errors.
  private val tempDir = project.underlying.getFolder(".tmpBin")
  private def tempDirFile = tempDir.getLocation().toFile()

  override var depFile: IFile = null

  override def clean(implicit monitor: IProgressMonitor) {
    cachePath.refreshLocal(IResource.DEPTH_ZERO, null)
    cachePath.delete(true, false, monitor)
    clearCached()
    // refresh explorer
  }
  override def invalidateAfterLoad: Boolean = true

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], pm: SubMonitor) {
    buildReporter.reset()
    val removedFiles = removed.map(EclipseResource(_): AbstractFile)
    val toBuild = addedOrUpdated.map(EclipseResource(_): AbstractFile) -- removedFiles
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
  }

  private def buildingFiles(included: scala.collection.Set[AbstractFile]) {
    included foreach {
      case EclipseResource(f: IFile) => FileUtils.clearTasks(f, null)
      case _                         =>
    }
  }

  def findInstallation(project: ScalaProject): ScalaInstallation = project.getDesiredInstallation()

  /** Inspired by IC.compile
   *
   *  We need to duplicate IC.compile (by inlining insde this
   *  private method) because the Java interface it has as a
   *  parameter serializes IncOptions to a String map, which is
   *  not expressive enough to use the transactional classfile
   *  manager (required for correctness).  In other terms, we
   *  need a richer (IncOptions) parameter type, here.
   */
  private def aggressiveCompile(in: SbtInputs, log: Logger): Analysis = {
    val options = in.options; import options.{ options => scalacOptions, _ }
    val compilers = in.compilers
    val agg = new AggressiveCompile(cacheFile)
    val aMap = (f: File) => m2o(in.analysisMap(f))
    val defClass = (f: File) => { val dc = Locator(f); (name: String) => dc.apply(name) }

    compilers match {
      case Right(comps) =>
        import comps._
        agg(scalac, javac, options.sources, classpath, output, in.cache, m2o(in.progress), scalacOptions, javacOptions, aMap,
          defClass, sbtReporter, order, skip = false, in.incOptions)(log)
      case Left(errors) =>
        buildReporter.error(NoPosition, errors)
        throw CompilerInterfaceFailed
    }
  }

  private object CompilerInterfaceFailed extends RuntimeException
}

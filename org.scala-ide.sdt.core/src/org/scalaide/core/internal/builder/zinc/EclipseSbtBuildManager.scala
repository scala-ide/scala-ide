package org.scalaide.core.internal.builder
package zinc

import java.io.File
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable
import scala.tools.nsc.Settings

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.OperationCanceledException
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.internal.builder.CachedAnalysisBuildManager
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.core.internal.builder.TaskManager
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.util.internal.SbtUtils
import org.scalaide.util.internal.Suppress.DeprecatedWarning.AggressiveCompile
import org.scalaide.util.internal.Suppress.DeprecatedWarning.aggressivelyCompile

import sbt.Logger.xlog2Log
import sbt.compiler.CompileFailed
import sbt.compiler.IC
import sbt.inc.Analysis
import sbt.inc.IncOptions
import sbt.inc.SourceInfo
import xsbti.F0
import xsbti.Logger
import xsbti.compile.CompileProgress

/** An Eclipse builder using the Sbt engine.
 *
 *  The classpath is handled by delegating to the underlying project. That means
 *  a valid Scala library has to exist on the classpath, but it's not limited to
 *  being called 'scala-library.jar': @see [[org.scalaide.core.internal.project.ClasspathManagement]] for
 *  how the library is resolved (it can be any jar or even an existing dependent project).
 *
 *  It uses the configured ScalaInstallation, meaning it can build with different versions of Scala.
 */
class EclipseSbtBuildManager(val project: IScalaProject, settings: Settings, analysisCache: Option[IFile] = None,
  addToClasspath: Seq[IPath] = Seq.empty, srcOutputs: Seq[(IContainer, IContainer)] = Seq.empty)
    extends CachedAnalysisBuildManager with HasLogger {

  /** Initialized in `build`, used by the SbtProgress. */
  private var monitor: SubMonitor = _

  private val sources: mutable.Set[IFile] = mutable.Set.empty
  private val cached = new AtomicReference[SoftReference[Analysis]]

  def analysisStore = analysisCache.getOrElse(project.underlying.getFile(".cache"))
  private def cacheFile = analysisStore.getLocation.toFile

  // this directory is used by Sbt to store classfiles between
  // compilation runs to implement all-or-nothing compilation
  // sementics. Original files are copied over to tempDir and
  // moved back in case of compilation errors.
  private val tempDir = project.underlying.getFolder(".tmpBin")
  private def tempDirFile = tempDir.getLocation().toFile()

  private val sbtLogger = new xsbti.Logger {
    override def error(msg: F0[String]) = logger.error(msg())
    override def warn(msg: F0[String]) = logger.warn(msg())
    override def info(msg: F0[String]) = logger.info(msg())
    override def debug(msg: F0[String]) = logger.debug(msg())
    override def trace(exc: F0[Throwable]) = logger.error("", exc())
  }

  private lazy val sbtReporter = new SbtBuildReporter(project)

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], pm: SubMonitor): Unit = {
    sbtReporter.reset()
    val toBuild = addedOrUpdated -- removed
    monitor = pm
    hasInternalErrors = false
    try {
      update(toBuild, removed)
    } catch {
      case oce: OperationCanceledException =>
        throw oce
      case e: Throwable =>
        hasInternalErrors = true
        BuildProblemMarker.create(project, e)
        eclipseLog.error("Error in Scala compiler", e)
        sbtReporter.log(SbtUtils.NoPosition, "SBT builder crashed while compiling. The error message is '" + e.getMessage() + "'. Check Error Log for details.", xsbti.Severity.Error)
    }
    hasInternalErrors = sbtReporter.hasErrors || hasInternalErrors
  }

  override def clean(implicit monitor: IProgressMonitor): Unit = {
    analysisStore.refreshLocal(IResource.DEPTH_ZERO, null)
    analysisStore.delete(true, false, monitor)
    clearCached()
  }

  override def invalidateAfterLoad: Boolean = true

  override def canTrackDependencies: Boolean = true

  def findInstallation(project: IScalaProject): IScalaInstallation = project.effectiveScalaInstallation()

  /** Remove the given files from the managed build process. */
  private def removeFiles(files: scala.collection.Set[IFile]): Unit = {
    if (!files.isEmpty)
      sources --= files
  }

  /** The given files have been modified by the user. Recompile
   *  them and their dependent files.
   */
  private def update(added: scala.collection.Set[IFile], removed: scala.collection.Set[IFile]): Unit = {
    if (added.isEmpty && removed.isEmpty)
      logger.info("No changes in project, running the builder for potential transitive changes.")
    clearTasks(added)
    removeFiles(removed)
    sources ++= added
    runCompiler(asJFiles(sources))
  }

  private def asJFiles(files: scala.collection.Set[IFile]): Seq[File] =
    files.map(ifile => ifile.getLocation.toFile).toSeq

  private def clearTasks(included: scala.collection.Set[IFile]): Unit = {
    included foreach TaskManager.clearTasks
  }

  private def runCompiler(sources: Seq[File]): Unit = {
    val scalaInstall = findInstallation(project)
    logger.info(s"Running compiler using $scalaInstall")
    val progress = new SbtProgress
    val inputs = new SbtInputs(scalaInstall, sources, project, monitor, progress, tempDirFile, sbtLogger,
      addToClasspath, srcOutputs)
    val analysis =
      try
        Some(aggressiveCompile(inputs, sbtLogger))
      catch {
        case _: CompileFailed | CompilerInterfaceFailed => None
      }
    analysis foreach setCached
    createAdditionalMarkers(analysis.getOrElse(latestAnalysis(inputs.incOptions)), progress.actualCompiledFiles)
  }

  /**
   * Create markers for problems (errors or warnings) in the given analysis object that were *not* reported.
   *
   * The incremental compiler may not recompile files with warnings (like deprecation warnings), meaning
   * we won't get any warnings through the reporter. However, at the beginning of the build we removed all
   * markers in the current project, so now we need to recreate markers only for files that were *not* recompiled
   * in the last run.
   *
   * @note See discussion under https://github.com/sbt/sbt/issues/1372
   */
  private def createAdditionalMarkers(analysis: Analysis, compiledFiles: Set[IFile]): Unit = {
    for {
      (file, info) <- analysis.infos.allInfos
      resource <- ResourcesPlugin.getWorkspace.getRoot.findFilesForLocationURI(file.toURI())
      // this file might have been deleted in the meantime
      if resource.exists() && !compiledFiles(resource)
    } createMarkers(info)
  }

  /**
   * Create problem markers for the given source info.
   */
  private def createMarkers(sourceInfo: SourceInfo) = {
    for (problem <- sourceInfo.reportedProblems)
      sbtReporter.createMarker(problem.position, problem.message, problem.severity)
  }

  private def setCached(a: Analysis): Analysis = {
    cached set new SoftReference[Analysis](a); a
  }
  private def clearCached(): Unit = {
    Option(cached.get) foreach (ref => ref.clear)
  }

  // take by-name argument because we need incOptions only when we have a cache miss
  override def latestAnalysis(incOptions: => IncOptions): Analysis =
    Option(cached.get) flatMap (ref => Option(ref.get)) getOrElse setCached(IC.readAnalysis(cacheFile, incOptions))

  /**
   * Knows nothing about output files.
   */
  override def buildManagerOf(outputFile: File): Option[EclipseBuildManager] = None

  /**
   * Knows nothing about errors.
   */
  override def buildErrors: Set[IMarker] = Set.empty

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
    val aMap = (f: File) => SbtUtils.m2o(in.analysisMap(f))
    val defClass = (f: File) => { val dc = Locator(f); (name: String) => dc.apply(name) }

    compilers match {
      case Right(comps) =>
        import comps._
        aggressivelyCompile(agg)(log)(scalac, javac, options.sources, classpath, output, in.cache, SbtUtils.m2o(in.progress),
          scalacOptions, javacOptions, aMap, defClass, sbtReporter, order, /* skip = */ false, in.incOptions)
      case Left(errors) =>
        sbtReporter.log(SbtUtils.NoPosition, errors, xsbti.Severity.Error)
        throw CompilerInterfaceFailed
    }
  }

  private object CompilerInterfaceFailed extends RuntimeException

  private class SbtProgress extends CompileProgress {
    private var lastWorked = 0
    private var savedTotal = 0
    private var throttledMessages = 0

    private var compiledFiles: Set[IFile] = Set()

    /** Return the set of files that were reported as being compiled during this session */
    def actualCompiledFiles: Set[IFile] = compiledFiles

    override def startUnit(phaseName: String, unitPath: String): Unit = {
      def unitIPath: IPath = Path.fromOSString(unitPath)

      if (monitor.isCanceled)
        throw new OperationCanceledException

      // dirty-hack for ticket #1001595 until Sbt provides a better API for tracking sources recompiled by the incremental compiler
      if (phaseName == "parser") {
        val file = FileUtils.resourceForPath(unitIPath, project.underlying.getFullPath)
        file.foreach { f =>
          TaskManager.clearTasks(f)
          compiledFiles += f
        }
      }

      // It turned out that updating `subTaks` too often slowed down the build... and the UI was blocked
      // going through all the updates, long after compilation finished. So we report only 1:10 updates.
      throttledMessages += 1
      if (throttledMessages == 10) {
        throttledMessages = 0
        val projectPath = project.javaProject.getProject.getLocation
        monitor.subTask("phase " + phaseName + " for " + unitIPath.makeRelativeTo(projectPath))
      }
    }

    override def advance(current: Int, total: Int): Boolean =
      if (monitor.isCanceled) {
        throw new OperationCanceledException
      } else {
        if (savedTotal != total) {
          monitor.setWorkRemaining(total - lastWorked)
          savedTotal = total
        }

        if (lastWorked < current) {
          monitor.worked(current - lastWorked)
          lastWorked = current
        }
        true
      }
  }
}

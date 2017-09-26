package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.util.Optional
import java.util.function.Supplier

import scala.reflect.internal.util.NoPosition
import scala.reflect.internal.util.Position

import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation.scalaInstanceForInstallation
import org.scalaide.logging.HasLogger

import sbt.internal.inc.AnalyzingCompiler
import sbt.internal.inc.FreshCompilerCache
import sbt.internal.inc.IncrementalCompilerImpl
import xsbti.Logger
import xsbti.compile.ClasspathOptions
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompilerBridgeProvider
import xsbti.compile.DefinesClass
import xsbti.compile.IncOptions
import xsbti.compile.MiniSetup
import xsbti.compile.PerClasspathEntryLookup
import xsbti.compile.ScalaInstance
import org.eclipse.core.runtime.IPath

object ResidentCompiler {
  def apply(project: IScalaProject, worksheetBinFolder: File, worksheetLibs: Option[IPath]) = {
    val installation = project.effectiveScalaInstallation
    val scalaInstance = scalaInstanceForInstallation(installation)
    val store = ScalaPlugin().compilerBridgeStore
    val comps = store.compilerBridgeFor(installation)(new NullProgressMonitor).right.map {
      compilerBridge =>
        // prevent zinc from adding things to the (boot)classpath
        val cpOptions = ClasspathOptions.create(false, false, false, /* autoBoot = */ false, /* filterLibrary = */ false)
        Compilers(
          new AnalyzingCompiler(
            scalaInstance,
            new CompilerBridgeProvider {
              def fetchCompiledBridge(si: ScalaInstance, logger: Logger) = si.version match {
                case scalaInstance.version => compilerBridge.toFile
                case requested => throw new IllegalStateException(s"${scalaInstance.version} does not match requested one $requested")
              }
              def fetchScalaInstance(scalaVersion: String, logger: Logger) = scalaVersion match {
                case scalaInstance.version => scalaInstance
                case requested => throw new IllegalStateException(s"${scalaInstance.version} does not match requested one $requested")
              }
            },
            cpOptions,
            _ ⇒ (),
            None),
          new JavaEclipseCompiler(null, null))
    }
    comps.toOption.map { comps => new ResidentCompiler(project, comps, worksheetBinFolder, worksheetLibs) }
  }

  sealed abstract class CompilationResult
  case object CompilationSuccess extends CompilationResult
  case class CompilationFailed(errors: Iterable[CompilationError]) extends CompilationResult

  case class CompilationError(msg: String, pos: Position)
}

class ResidentCompiler private (project: IScalaProject, comps: Compilers, worksheetBinFolder: File, libs: Option[IPath]) extends HasLogger {
  import ResidentCompiler._
  private val sbtLogger = new xsbti.Logger {
    override def error(msg: Supplier[String]) = logger.error(msg.get)
    override def warn(msg: Supplier[String]) = logger.warn(msg.get)
    override def info(msg: Supplier[String]) = logger.info(msg.get)
    override def debug(msg: Supplier[String]) = logger.debug(msg.get)
    override def trace(exc: Supplier[Throwable]) = logger.error("", exc.get)
  }
  private val worksheetLibs = libs.map(_.toFile).toSeq

  def compile(worksheetSrc : File): CompilationResult = {
    //val worksheetSrc = ResourcesPlugin.getWorkspace.getRoot
    def sbtReporter = new SbtBuildReporter(project)

    def incOptions: IncOptions = {
      import xsbti.compile.IncOptions._
      val base = of()
      base.
        withApiDebug(defaultApiDebug()).
        withRelationsDebug(defaultRelationsDebug()).
        withClassfileManagerType(Optional.empty()).
        withApiDumpDirectory(Optional.empty()).
        withRecompileOnMacroDef(defaultRecompileOnMacroDef()).
        withEnabled(defaultEnabled()).
        withStoreApis(defaultStoreApis()).
        withApiDiffContextSize(defaultApiDiffContextSize()).
        withExternalHooks(defaultExternal()).
        withExtra(defaultExtra()).
        withLogRecompileOnMacro(defaultLogRecompileOnMacro()).
        withRecompileAllFraction(defaultRecompileAllFraction()).
        withRelationsDebug(defaultRelationsDebug()).
        withTransitiveStep(defaultTransitiveStep()).
        withUseCustomizedFileManager(defaultUseCustomizedFileManager()).
        withUseOptimizedSealed(defaultUseOptimizedSealed())
    }

    val lookup = new PerClasspathEntryLookup {
      override def analysis(classpathEntry: File) =
        Optional.empty()

      override def definesClass(classpathEntry: File) = {
        val dc = Locator(classpathEntry)
        new DefinesClass() {
          def apply(name: String) = dc.apply(name)
        }
      }
    }

    def srcOutDirs = Seq(worksheetSrc.toPath.getParent.toFile -> worksheetBinFolder)

    def output = new EclipseMultipleOutput(srcOutDirs)
    def cache = new FreshCompilerCache

    val classpath = worksheetLibs ++ project.scalaClasspath.userCp.map(_.toFile)

    import xsbti.compile.CompileOrder._

    new IncrementalCompilerImpl().compile(comps.scalac, comps.javac, Array(worksheetSrc), classpath.toArray, output,
      cache, project.scalacArguments.toArray, javaOptions = Array(), Optional.empty[CompileAnalysis], Optional.empty[MiniSetup],
      lookup, sbtReporter, ScalaThenJava, skip = false, Optional.empty() /*progress*/ , incOptions, extra = Array(), sbtLogger)
    import xsbti.Severity._
    val errors = sbtReporter.problems.collect {
      case p if p.severity() == Error =>
        val pos = if (p.position().line().isPresent())
          new Position {
            override def line = p.position().line().get
          }
        else NoPosition
        CompilationError(p.message(), pos)
    }
    if (errors.nonEmpty)
      CompilationFailed(errors)
    else
      CompilationSuccess
  }
}
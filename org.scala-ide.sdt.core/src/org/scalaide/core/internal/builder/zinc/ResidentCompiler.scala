package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.util.Optional

import scala.reflect.internal.util.NoPosition
import scala.reflect.internal.util.Position
import scala.tools.nsc.settings.SpecificScalaVersion

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaProject
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.SbtUtils

import sbt.internal.inc.FreshCompilerCache
import sbt.internal.inc.IncrementalCompilerImpl
import xsbti.compile.CompileAnalysis
import xsbti.compile.CompileOrder
import xsbti.compile.CompileProgress
import xsbti.compile.IncOptions
import xsbti.compile.MiniSetup

object ResidentCompiler {
  def apply(project: IScalaProject, compilationOutputFolder: File, extraLibsToCompile: Option[IPath],
      monitor: SubMonitor = SubMonitor.convert(new NullProgressMonitor)) = {
    val installation = project.effectiveScalaInstallation
    val comps = compilers(installation, monitor)
    comps.toOption.map { comps => new ResidentCompiler(project, comps, compilationOutputFolder, extraLibsToCompile) }
  }

  sealed abstract class CompilationResult
  case object CompilationSuccess extends CompilationResult
  case class CompilationFailed(errors: Iterable[CompilationError]) extends CompilationResult

  case class CompilationError(msg: String, pos: Position)
}

class ResidentCompiler private (project: IScalaProject, comps: Compilers, compilationOutputFolder: File,
    extraLibsToCompile: Option[IPath]) extends HasLogger {
  import ResidentCompiler._
  private val sbtLogger = SbtUtils.defaultSbtLogger(logger)
  private val libs = extraLibsToCompile.map(_.toFile).toSeq

  def compile(compiledSource: File): CompilationResult = {
    def sbtReporter = new SbtBuildReporter(project)
    def incOptions: IncOptions = IncOptions.of()
    def output = new EclipseMultipleOutput(Seq(compiledSource.toPath.getParent.toFile -> compilationOutputFolder))
    def cache = new FreshCompilerCache
    val lookup = new DefaultPerClasspathEntryLookup {}
    val classpath = libs ++ project.scalaClasspath.userCp.map(_.toFile)
    val scalacOpts = project.effectiveScalaInstallation.version match {
      case SpecificScalaVersion(2, 10, _, _) =>
        project.scalacArguments.filterNot(opt => opt == "-Xsource:2.10" || opt == "-Ymacro-expand:none")
      case _ => project.scalacArguments
    }

    new IncrementalCompilerImpl().compile(comps.scalac, comps.javac, Array(compiledSource), classpath.toArray, output,
      cache, scalacOpts.toArray, javaOptions = Array(), Optional.empty[CompileAnalysis], Optional.empty[MiniSetup],
      lookup, sbtReporter, CompileOrder.ScalaThenJava, skip = false, Optional.empty[CompileProgress], incOptions, extra = Array(),
      sbtLogger)

    import xsbti.Severity._
    val errors = sbtReporter.problems.collect {
      case p if p.severity == Error =>
        val pos = p.position.line.map[Position] { pline =>
          new Position {
            override def line = pline
          }
        }.orElse { NoPosition }
        CompilationError(p.message, pos)
    }
    errors.toSeq match {
      case errors @ _ +: _ => CompilationFailed(errors)
      case Nil => CompilationSuccess
    }
  }
}
package org.scalaide.core.internal.builder.zinc

import java.io.File

import org.scalaide.util.internal.SbtUtils

import sbt.internal.inc.Analysis
import sbt.internal.inc.IncrementalCompilerImpl
import sbt.internal.inc.MixedAnalyzingCompiler
import xsbti.Logger
import xsbti.Reporter
import xsbti.compile.CompileResult
import xsbti.compile.DefinesClass
import xsbti.compile.JavaCompiler
import xsbti.compile.PerClasspathEntryLookup
import xsbti.compile.ScalaCompiler

/**
 * Contains a Scala and a Java compiler. Should be used instead of
 * [[xsbti.compile.Compilers]]. The latter got a new API in zinc, which we don't
 * need to adapt.
 */
final case class Compilers(scalac: ScalaCompiler, javac: JavaCompiler)

class CachingCompiler private (cacheFile: File, sbtReporter: Reporter, log: Logger) {
  /**
   * Inspired by `IC.compile` and `AggressiveCompile.compile1`
   *
   *  We need to duplicate `IC.compile`, because Java interface passes the incremental
   *  compiler options `IncOptions` as `Map[String, String]`, which is not expressive
   *  enough to use the transactional classfile manager (required for correctness).
   *  In other terms, we need richer (`IncOptions`) parameter type, here.
   *  Other thing is the update of the `AnalysisStore` implemented in `AggressiveCompile.compile1`
   *  method which is not implemented in `IC.compile`.
   */
  def compile(in: SbtInputs, comps: Compilers): Analysis = {
    val lookup = new PerClasspathEntryLookup {
      override def analysis(classpathEntry: File) =
        SbtUtils.m2jo(in.analysisMap(classpathEntry))

      override def definesClass(classpathEntry: File) = {
        val dc = Locator(classpathEntry)
        new DefinesClass() {
          def apply(name: String) = dc.apply(name)
        }
      }
    }
    val (previousAnalysis, previousSetup) = SbtUtils.readCache(cacheFile)
      .map {
        case (a, s) => (Option(a), Option(s))
      }.getOrElse((Option(SbtUtils.readAnalysis(cacheFile)), None))
    cacheAndReturnLastAnalysis(new IncrementalCompilerImpl().compile(comps.scalac, comps.javac, in.sources, in.classpath, in.output, in.cache,
      in.scalacOptions, in.javacOptions, SbtUtils.o2jo(previousAnalysis), SbtUtils.o2jo(previousSetup), lookup, sbtReporter, in.order,
      skip = false, SbtUtils.m2jo(in.progress), in.incOptions, extra = Array(), log))
  }

  private def cacheAndReturnLastAnalysis(compilationResult: CompileResult): Analysis = {
    if (compilationResult.hasModified)
      AnalysisStore.materializeLazy(MixedAnalyzingCompiler.staticCachedStore(cacheFile)).set(compilationResult.analysis, compilationResult.setup)
    compilationResult.analysis match {
      case a: Analysis => a
      case a => throw new IllegalStateException(s"object of type `Analysis` was expected but got `${a.getClass}`.")
    }
  }
}

object CachingCompiler {
  def apply(cacheFile: File, sbtReporter: Reporter, logger: Logger): CachingCompiler =
    new CachingCompiler(cacheFile, sbtReporter, logger)
}

package org.scalaide.core.internal.builder.zinc

import java.io.File
import org.scalaide.util.internal.SbtUtils
import sbt.CompileSetup
import sbt.EmptyAnalysis
import sbt.compiler.AnalyzingCompiler
import sbt.compiler.IC
import sbt.compiler.MixedAnalyzingCompiler
import sbt.inc.Analysis
import sbt.inc.AnalysisStore
import xsbti.Logger
import xsbti.Reporter
import xsbti.compile.Compilers

private object Cache {
  type Result = (Analysis, Option[CompileSetup])

  private[zinc] def previousResult(cacheFile: File, initialNameHashing: Boolean): Result = {
    Cache(cacheFile).get().map {
      case (analysis, setup) => (analysis, Some(setup))
    } getOrElse {
      (EmptyAnalysis(initialNameHashing), None)
    }
  }

  private[zinc] def apply(cacheFile: File): AnalysisStore =
    MixedAnalyzingCompiler.staticCachedStore(cacheFile)
}

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
  def compile(in: SbtInputs, comps: Compilers[AnalyzingCompiler]): Analysis = {
    val options = in.options; import options.{ options => scalacOptions, _ }
    val aMap = (f: File) => SbtUtils.m2o(in.analysisMap(f))
    val defClass = (f: File) => { val dc = Locator(f); (name: String) => dc.apply(name) }
    val (previousAnalysis, previousSetup) = Cache.previousResult(cacheFile, in.incOptions.nameHashing)
    import comps._
    cacheAndReturnLastAnalysis(IC.incrementalCompile(scalac, javac, options.sources, classpath, output, in.cache,
      SbtUtils.m2o(in.progress), scalacOptions, javacOptions, previousAnalysis, previousSetup, aMap, defClass,
      sbtReporter, order, skip = false, in.incOptions)(log))
  }

  private def cacheAndReturnLastAnalysis(compilationResult: IC.Result): Analysis = {
    if (compilationResult.hasModified)
      Cache(cacheFile).set(compilationResult.analysis, compilationResult.setup)
    compilationResult.analysis
  }
}

object CachingCompiler {
  def apply(cacheFile: File, sbtReporter: Reporter, logger: Logger): CachingCompiler =
    new CachingCompiler(cacheFile, sbtReporter, logger)
}


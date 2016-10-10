package org.scalaide.core.internal.builder.zinc

import sbt.internal.inc.Analysis

import xsbti.compile.CompileAnalysis
import xsbti.compile.MiniSetup

object AnalysisStore {
  import sbt.internal.inc.{ AnalysisStore => SbtAnalysisStore }
  def materializeLazy(backing: SbtAnalysisStore): SbtAnalysisStore = new SbtAnalysisStore {
    private def materializeApis(analysis: CompileAnalysis, setup: MiniSetup) = {
      if (setup.storeApis()) {
        val apis = analysis match { case a: Analysis => a.apis }
        apis.internal.foreach { case (_, v) => v.api }
        apis.external.foreach { case (_, v) => v.api }
      }
      (analysis, setup)
    }
    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      Function.tupled(backing.set _)(materializeApis(analysis, setup))
    }
    def get(): Option[(CompileAnalysis, MiniSetup)] = backing.get()
  }
}
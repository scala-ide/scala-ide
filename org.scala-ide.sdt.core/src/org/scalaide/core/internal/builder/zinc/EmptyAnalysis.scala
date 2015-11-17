import sbt.inc.Analysis

package sbt {
  /** Just to expose `Analysis.empty(nameHashing: Boolean)` to non `sbt` packages. */
  object EmptyAnalysis {
    def apply(nameHashing: Boolean): Analysis = Analysis.empty(nameHashing)
  }
}

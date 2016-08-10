package org.scalaide.core.internal.builder

import sbt.inc.IncOptions
import sbt.inc.Analysis

trait Analyzable {
  /** Gives back the latest dependencies analysis done by underlying compiler. */
  def latestAnalysis(incOptions: => IncOptions): Analysis
}

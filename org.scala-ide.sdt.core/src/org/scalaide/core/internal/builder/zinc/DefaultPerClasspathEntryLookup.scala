package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.util.Optional

import xsbti.compile.CompileAnalysis
import xsbti.compile.DefinesClass
import xsbti.compile.PerClasspathEntryLookup

private[zinc] trait DefaultPerClasspathEntryLookup extends PerClasspathEntryLookup {
  override def analysis(classpathEntry: File): Optional[CompileAnalysis] =
    Optional.empty()

  override def definesClass(classpathEntry: File) = {
    val dc = Locator(classpathEntry)
    new DefinesClass() {
      def apply(name: String) = dc.apply(name)
    }
  }
}

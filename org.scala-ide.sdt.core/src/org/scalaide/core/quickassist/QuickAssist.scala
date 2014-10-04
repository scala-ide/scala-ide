package org.scalaide.core.quickassist

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.text.java.IProblemLocation

trait QuickAssist {
  def compute(ctx: InvocationContext): Seq[BasicCompletionProposal]
}

case class InvocationContext(
  compilationUnit: ICompilationUnit,
  selectionStart: Int,
  selectionLength: Int,
  problemLocations: Seq[IProblemLocation]
)

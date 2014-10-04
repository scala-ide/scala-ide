package org.scalaide.core.quickassist

import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

trait QuickAssist {
  def compute(ctx: InvocationContext): Seq[BasicCompletionProposal]
}

case class InvocationContext(
  sourceFile: ScalaSourceFile,
  selectionStart: Int,
  selectionLength: Int,
  problemLocations: Seq[IProblemLocation]
)

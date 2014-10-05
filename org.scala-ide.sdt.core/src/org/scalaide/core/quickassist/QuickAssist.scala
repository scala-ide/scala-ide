package org.scalaide.core.quickassist

import org.eclipse.jface.text.source.Annotation
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

trait QuickAssist {
  def compute(ctx: InvocationContext): Seq[BasicCompletionProposal]
}

case class InvocationContext(
  sourceFile: ScalaSourceFile,
  selectionStart: Int,
  selectionLength: Int,
  problemLocations: Seq[AssistLocation]
)

case class AssistLocation(
  offset: Int,
  length: Int,
  annotation: Annotation
)

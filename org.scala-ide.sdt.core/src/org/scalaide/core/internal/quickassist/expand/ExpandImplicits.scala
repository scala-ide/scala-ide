package org.scalaide.core.internal.quickassist
package expand

import org.eclipse.jface.text.Position
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class ImplicitConversionExpandingProposal(s: String, pos: Position)
  extends ExpandingProposalBase(s, "Expand this implicit conversion: ", pos)

class ImplicitArgumentExpandingProposal(s: String, pos: Position)
  extends ExpandingProposalBase(s, "Explicitly inline the implicit arguments: ", pos)

object ExpandImplicits {
  private final val ImplicitConversionFound = "(?s)Implicit conversions found: (.*)".r
  private final val ImplicitArgFound = "(?s)Implicit arguments found: (.*)".r
}

class ExpandImplicits extends QuickAssist {
  import ExpandImplicits._
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val assists = EditorUtils.openEditorAndApply(ctx.sourceFile) { editor =>
      EditorUtils.getAnnotationsAtOffset(editor, ctx.selectionStart) flatMap {
        case (ann, pos) =>
          ann.getText match {
            case ImplicitConversionFound(s) => List(new ImplicitConversionExpandingProposal(s, pos))
            case ImplicitArgFound(s)        => List(new ImplicitArgumentExpandingProposal(s, pos))
            case _                          => Nil
          }
      }
    }
    assists.toList
  }
}

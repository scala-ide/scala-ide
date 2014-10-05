package org.scalaide.core.internal.quickassist
package expand

import org.eclipse.jface.text.Position
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class ImplicitConversionExpandingProposal(s: String, offset: Int, length: Int)
  extends ExpandingProposalBase(s, "Expand this implicit conversion: ", offset, length)

class ImplicitArgumentExpandingProposal(s: String, offset: Int, length: Int)
  extends ExpandingProposalBase(s, "Explicitly inline the implicit arguments: ", offset, length)

object ExpandImplicits {
  private final val ImplicitConversionFound = "(?s)Implicit conversion found: `(.*?)` => `(.*):.*?`".r
  private final val ImplicitArgFound = "(?s)Implicit arguments found: `(.*?)` => `(.*?)`".r
}

class ExpandImplicits extends QuickAssist {
  import ExpandImplicits._

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    ctx.problemLocations flatMap { location =>
      location.annotation.getText match {
        case ImplicitConversionFound(from, to) =>
          List(new ImplicitConversionExpandingProposal(s"$from => $to", location.offset, location.length))
        case ImplicitArgFound(from, to) =>
          List(new ImplicitArgumentExpandingProposal(s"$from => $to", location.offset, location.length))
        case _ =>
          Nil
      }
    }
  }
}

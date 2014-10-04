package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.Position
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.quickassist.abstractimpl.ImplAbstractMembers
import org.scalaide.core.internal.quickassist.explicit.ExplicitReturnType
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.logging.HasLogger
import org.scalaide.refactoring.internal.extract.ExtractionProposal
import org.scalaide.util.eclipse.EditorUtils

/**
 * Enables all quick fixes that don't resolve errors in the document. Instead they
 * just apply refactorings.
 */
class ScalaQuickAssistProcessor extends QuickAssist with HasLogger {

  import ScalaQuickAssistProcessor._

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val (start, len, ssf) = (ctx.selectionStart, ctx.selectionLength, ctx.sourceFile)

    import EditorUtils._
    val assists = openEditorAndApply(ssf) { editor =>
      val corrections = getAnnotationsAtOffset(editor, start) flatMap {
        case (ann, pos) =>
          suggestAssist(ann.getText, pos)
      }
      corrections.toArray.distinct
    }

    val allAssists = ExplicitReturnType.suggestsFor(ssf, start).toArray ++
      ImplAbstractMembers.suggestsFor(ssf, start) ++ assists ++
      ExtractionProposal.getQuickAssistProposals(ssf, start, start + len)

    allAssists.toSeq.asInstanceOf[Seq[BasicCompletionProposal]]
  }

  private def suggestAssist(problemMessage: String, location: Position): Seq[IJavaCompletionProposal] = {
    val refactoringSuggestions = try availableAssists.filter(_.isValidProposal) catch {
      case e: Exception =>
        logger.debug("Exception when building quick assist list: " + e.getMessage, e)
        Seq()
    }

    refactoringSuggestions ++
      (problemMessage match {
        case ImplicitConversionFound(s) => List(new ImplicitConversionExpandingProposal(s, location))
        case ImplicitArgFound(s) => List(new ImplicitArgumentExpandingProposal(s, location))
        case _ => Nil
      })
  }
}

object ScalaQuickAssistProcessor {
  private final val ImplicitConversionFound = "(?s)Implicit conversions found: (.*)".r

  private final val ImplicitArgFound = "(?s)Implicit arguments found: (.*)".r

  val availableAssists: Seq[ProposalRefactoringHandlerAdapter] = Seq(
    ExpandCaseClassBindingProposal,
    InlineLocalProposal)
}

package org.scalaide.core.internal.quickfix

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EditorUtils
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.text.java.IInvocationContext
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor
import org.eclipse.jface.text.Position
import org.scalaide.core.internal.quickfix.explicit.ExplicitReturnType
import org.scalaide.core.internal.quickfix.abstractimpl.ImplAbstractMembers
import org.scalaide.refactoring.internal.extract.ExtractionProposal

/**
 * Enables all quick fixes that don't resolve errors in the document. Instead they
 * just apply refactorings.
 */
class ScalaQuickAssistProcessor extends IQuickAssistProcessor with HasLogger {

  import ScalaQuickAssistProcessor._

  override def hasAssists(context: IInvocationContext): Boolean = true

  /**
   * Needs to return ``null`` when no assists could be found.
   */
  override def getAssists(context: IInvocationContext, locations: Array[IProblemLocation]): Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf: ScalaSourceFile =>
        import org.scalaide.util.eclipse.EditorUtils
        val assists = EditorUtils.openEditorAndApply(ssf) { editor =>
          val corrections = EditorUtils.getAnnotationsAtOffset(editor, context.getSelectionOffset()) flatMap {
            case (ann, pos) =>
              suggestAssist(context.getCompilationUnit(), ann.getText, pos)
          }
          corrections.toArray.distinct
        }

        val allAssists = ExplicitReturnType.suggestsFor(ssf, context.getSelectionOffset).toArray ++
          ImplAbstractMembers.suggestsFor(ssf, context.getSelectionOffset) ++ assists ++
          ExtractionProposal.getQuickAssistProposals(ssf, context.getSelectionOffset(), context.getSelectionOffset() + context.getSelectionLength())

        if (allAssists.isEmpty) null
        else allAssists
      case _ => null
    }

  private def suggestAssist(compilationUnit: ICompilationUnit, problemMessage: String, location: Position): Seq[IJavaCompletionProposal] = {
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
  private final val ImplicitConversionFound = "(?s)Implicit conversion found: (.*?):.*?".r

  private final val ImplicitArgFound = "(?s)Implicit arguments found: (.*?)".r

  val availableAssists = Seq(
    ExpandCaseClassBindingProposal,
    InlineLocalProposal)
}


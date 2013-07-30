package scala.tools.eclipse
package quickfix

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.text.java.IInvocationContext
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor
import org.eclipse.jface.text.Position

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
        import scala.tools.eclipse.util.EditorUtils._

        openEditorAndApply(ssf) { editor =>
          val corrections = getAnnotationsAtOffset(editor, context.getSelectionOffset()) flatMap {
            case (ann, pos) =>
              suggestAssist(context.getCompilationUnit(), ann.getText, pos)
          }
          if (corrections.isEmpty) null
          else corrections.toArray.distinct
        }
      case _ => null
    }

  private def suggestAssist(compilationUnit: ICompilationUnit, problemMessage: String, location: Position): Seq[IJavaCompletionProposal] = {
    val refactoringSuggestions: Seq[IJavaCompletionProposal] = try {
      List(
        ExtractLocalProposal,
        ExpandCaseClassBindingProposal,
        InlineLocalProposal,
        RenameProposal,
        ExtractMethodProposal).par.filter(_.isValidProposal).seq
    } catch {
      case e: Exception =>
        logger.debug("Exception when building quick assist list: " + e.getMessage, e)
        List()
    }

    refactoringSuggestions ++
      (problemMessage match {
        case ImplicitConversionFound(s) => List(new ImplicitConversionExpandingProposal(s, location))
        case ImplicitArgFound(s)        => List(new ImplicitArgumentExpandingProposal(s, location))
        case _                          => Nil
      })
  }
}

object ScalaQuickAssistProcessor {
  private final val ImplicitConversionFound = "(?s)Implicit conversions found: (.*)".r

  private final val ImplicitArgFound = "(?s)Implicit arguments found: (.*)".r
}


package scala.tools.eclipse
package quickfix

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.util.EditorUtils
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.text.java.IInvocationContext
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor
import org.eclipse.jface.text.Position
import scala.tools.eclipse.quickfix.explicit.ExplicitReturnType

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
        import EditorUtils._
        val assists = openEditorAndApply(ssf) { editor =>
          val corrections = getAnnotationsAtOffset(editor, context.getSelectionOffset()) flatMap {
            case (ann, pos) =>
              suggestAssist(context.getCompilationUnit(), ann.getText, pos)
          }
          corrections.toArray.distinct
        }
        val allAssists = ExplicitReturnType.suggestsFor(ssf, context.getSelectionOffset).toArray ++ assists

        if(allAssists.isEmpty) null
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
        case ImplicitArgFound(s)        => List(new ImplicitArgumentExpandingProposal(s, location))
        case _                          => Nil
      })
  }
}

object ScalaQuickAssistProcessor {
  private final val ImplicitConversionFound = "(?s)Implicit conversions found: (.*)".r

  private final val ImplicitArgFound = "(?s)Implicit arguments found: (.*)".r

  val availableAssists = Seq(
    ExtractLocalProposal,
    ExpandCaseClassBindingProposal,
    InlineLocalProposal,
    ExtractMethodProposal
  )
}


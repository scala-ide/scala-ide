package scala.tools.eclipse
package quickfix

import org.eclipse.ui.IEditorPart
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.core.runtime.CoreException
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.ISearchRequestor
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, INameEnvironment }
import org.eclipse.jdt.internal.core.{ DefaultWorkingCopyOwner, JavaProject }
import org.eclipse.jdt.internal.ui.text.correction.{ SimilarElement, SimilarElementsRequestor }
import org.eclipse.jdt.ui.text.java._
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.FileUtils
import scala.util.matching.Regex
import org.eclipse.jface.text.Position
import scala.collection.JavaConversions._
import scala.tools.eclipse.logging.HasLogger

class ScalaQuickAssistProcessor extends org.eclipse.jdt.ui.text.java.IQuickAssistProcessor with HasLogger {

  import ScalaQuickAssistProcessor._

  override def hasAssists(context: IInvocationContext): Boolean = true

  override def getAssists(context: IInvocationContext, locations: Array[IProblemLocation]): Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf: ScalaSourceFile => {
        import scala.tools.eclipse.util.EditorUtils.{openEditorAndApply, getAnnotationsAtOffset}
        openEditorAndApply(ssf) { editor =>
          val corrections = {
            for ((ann, pos) <- getAnnotationsAtOffset(editor, context.getSelectionOffset())) yield {
              suggestAssist(context.getCompilationUnit(), ann.getText, pos)
            }
          }.flatten.toList
          corrections match {
            case Nil        => null
            case correction => correction.distinct.toArray
          }
        }
      }
      // The caller expects null to mean "no assists".
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


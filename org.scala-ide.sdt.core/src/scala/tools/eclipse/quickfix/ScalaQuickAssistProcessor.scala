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

class ScalaQuickAssistProcessor extends org.eclipse.jdt.ui.text.java.IQuickAssistProcessor {

  import ScalaQuickAssistProcessor._
  
  override def hasAssists(context: IInvocationContext): Boolean = true

  override def getAssists(context: IInvocationContext, locations: Array[IProblemLocation]): Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf: ScalaSourceFile => {
        val editor = JavaUI.openInEditor(context.getCompilationUnit)
        val corrections = {
          for ((ann, pos) <- getAnnotationsAtOffset(editor, context.getSelectionOffset())) yield {
            suggestAssist(context.getCompilationUnit(), ann.getText, pos)
          }
        }.flatten
        corrections match {
          case Nil => null
          case correction => correction.distinct.toArray
        }
      }
      // The caller expects null to mean "no assists".
      case _ => null
    }

  private def getAnnotationsAtOffset(part: IEditorPart, offset: Int): List[Pair[Annotation, Position]] = {
    import ScalaQuickAssistProcessor._
    val model = JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)
    val annotationsWithPositions = model.getAnnotationIterator collect {
      case ann: Annotation => (ann, model.getPosition(ann))
    } 
    val annotationsAtOffset = annotationsWithPositions filter {
      case (_, pos) => isInside(offset, pos.offset, pos.offset + pos.length)
    }
    annotationsAtOffset.toList
  }

  private def suggestAssist(compilationUnit: ICompilationUnit, problemMessage: String, location: Position): List[IJavaCompletionProposal] = {

    problemMessage match {
      case ImplicitConversionFound(s) => List(new ImplicitConversionExpandingProposal(s, location))
      case ImplicitArgFound(s) => List(new ImplicitArgumentExpandingProposal(s, location))
      case _ => Nil
    }
  }
}

object ScalaQuickAssistProcessor {
  
  private def isInside(offset: Int, start: Int, end: Int) = {
    (start to end) contains offset
  }
  
  private final val ImplicitConversionFound = "Implicit conversions found: (.*)".r
  
  private final val ImplicitArgFound = "Implicit arguments found: (.*)".r
}


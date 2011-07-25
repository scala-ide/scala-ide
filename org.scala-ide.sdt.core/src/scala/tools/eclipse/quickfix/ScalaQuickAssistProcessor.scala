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

class ScalaQuickAssistProcessor extends org.eclipse.jdt.ui.text.java.IQuickAssistProcessor{
  private val implicitConversionFound = new Regex("Implicit conversions found: (.*)")
  private val implicitArgFound = new Regex("Implicit arguments found: (.*)")

  override def hasAssists(context: IInvocationContext) : Boolean = true

  // FIXME There's a lot of duplicated code from ScalaQuickFixProcessor in here;
  // maybe we could merge these two traits? --Mirko
  
  override def getAssists(context : IInvocationContext, locations : Array[IProblemLocation]) : Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf : ScalaSourceFile => {
      	val editor = JavaUI.openInEditor(context.getCompilationUnit)
          val corrections = {
          	for ((ann, pos) <- getAnnotationsAtOffset(editor, context.getSelectionOffset())) yield {
           	  suggestAssist(context.getCompilationUnit(), ann.getText, pos)
          	}
      	  }.flatten
          corrections match {
            case Nil => null
            case l => l.distinct.toArray
          }
        }
      case _ => null
  }
  
  private def getAnnotationsAtOffset(part: IEditorPart, offset: Int): List[Pair[Annotation, Position]] = {
	  import ScalaQuickAssistProcessor._ 
	  
    val model = JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)
    model.getAnnotationIterator collect {
	    case ann: Annotation => (ann, model.getPosition(ann))
	  } filter { 
	    case (_, pos) => isInside(offset, pos.offset, pos.offset + pos.length)
	  } toList
  }

  private def suggestAssist(compilationUnit: ICompilationUnit, problemMessage: String, location: Position): List[IJavaCompletionProposal] = {
	  
    problemMessage match {
      case implicitConversionFound(s) => List(new ImplicitConversionExpandingProposal(s,location)) 
      case implicitArgFound(s) =>   List(new ImplicitArgumentExpandingProposal(s,location))
      case _ => Nil
    }
  }
}

object ScalaQuickAssistProcessor {
	private def isInside(offset: Int, start: Int,end: Int): Boolean = {
		return offset == start || offset == end || (offset > start && offset < end); // make sure to handle 0-length ranges
    }
}


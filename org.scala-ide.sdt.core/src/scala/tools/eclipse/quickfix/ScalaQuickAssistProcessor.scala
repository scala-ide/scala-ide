package scala.tools.eclipse.quickfix

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

class ScalaQuickAssistProcessor extends org.eclipse.jdt.ui.text.java.IQuickAssistProcessor{
  private val implicitConversionFound = new Regex("Implicit conversions found: (.*)")
  private val implicitArgFound = new Regex("Implicit arguments found: (.*)")

  override def hasAssists(context: IInvocationContext) : Boolean = true

  override def getAssists(context : IInvocationContext, locations : Array[IProblemLocation]) : Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf : ScalaSourceFile => {
    	val editor = JavaUI.openInEditor(context.getCompilationUnit)
        var corrections : List[IJavaCompletionProposal] = Nil
        for (location <- locations)
        	for (ann <- getAnnotationsAtOffset(editor, location.getOffset)) {
         	  val fix = suggestAssist(context.getCompilationUnit(), ann.getText, location)
              corrections = corrections ++ fix
        	}
        corrections match {
          case Nil => null
          case l => l.distinct.toArray
        }
      }
      case _ => null
  }
  
  private def getAnnotationsAtOffset(part: IEditorPart, offset: Int): List[Annotation] = {
	  import ScalaQuickAssistProcessor._ 
	  
	  var ret = List[Annotation]() 
	  val model = JavaUI.getDocumentProvider().getAnnotationModel(part.getEditorInput())
	  val iter = model.getAnnotationIterator
	  while (iter.hasNext()) {
	 	val ann: Annotation = iter.next().asInstanceOf[Annotation]
	 	val pos = model.getPosition(ann)
	 	if (isInside(offset, pos.offset, pos.offset + pos.length))
	 	    ret = ann :: ret
	  }
	  return ret
  }
  

  private def suggestAssist(compilationUnit: ICompilationUnit, problemMessage: String, location: IProblemLocation): List[IJavaCompletionProposal] = {
	  
    return problemMessage match {
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


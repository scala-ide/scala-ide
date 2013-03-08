package scala.tools.eclipse.quickfix

// Eclipse
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.ISearchRequestor
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction }
import org.eclipse.jdt.internal.core.{ DefaultWorkingCopyOwner, JavaProject }
import org.eclipse.jdt.internal.ui.text.correction.{ SimilarElement, SimilarElementsRequestor }
import org.eclipse.jdt.ui.text.java._
import org.eclipse.jdt.core.search.{ TypeNameMatch, SearchEngine }
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.IDocument

// Scala IDE
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.util.EditorUtils.getAnnotationsAtOffset
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitHighlightingPresenter
import scala.tools.eclipse.logging.HasLogger

// Scala
import scala.util.matching.Regex
import collection.JavaConversions._

class ScalaQuickFixProcessor extends IQuickFixProcessor with HasLogger {
  private val typeNotFoundError = new Regex("not found: type (.*)")
  private val valueNotFoundError = new Regex("not found: value (.*)")
  private val xxxxxNotFoundError = new Regex("not found: (.*)")

  // regex for extracting expected and required type on type mismatch
  private val typeMismatchError = new Regex("type mismatch;\\s*found\\s*: (\\S*)\\s*required: (.*)")

  /**
   * Checks if the processor has any corrections.
   *
   * Currently this always returns true. At some point it may be worthwhile
   * to expend some effort on implementing this properly to make the plug-in
   * slightly more responsive.
   */
  def hasCorrections(unit : ICompilationUnit, problemId : Int) : Boolean = true

  /**
   * Collects corrections or code manipulations for the given context.
   *
   * @param context Defines current compilation unit, position and a shared AST
   * @param locations Problems are the current location.
   * @return the corrections applicable at the location or <code>null</code> if no proposals
   *      can be offered
   * @throws CoreException CoreException can be thrown if the operation fails
   */
  def getCorrections(context : IInvocationContext, locations : Array[IProblemLocation]) : Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf : ScalaSourceFile => {
    	val editor = JavaUI.openInEditor(context.getCompilationUnit)
        var corrections : List[IJavaCompletionProposal] = Nil
        for (location <- locations)
        	for ((ann, pos) <- getAnnotationsAtOffset(editor, location.getOffset)) {
         	  val importFix = suggestImportFix(context.getCompilationUnit(), ann.getText)
         	
         	  // compute all possible type mismatch quick fixes
         	  val document = (editor.asInstanceOf[ITextEditor]).getDocumentProvider().getDocument(editor.getEditorInput())
         	  val typeMismatchFix = suggestTypeMismatchFix(document, ann.getText, pos)
         	
         	  // concatenate lists of found quick fixes
            corrections = corrections ++ importFix ++ typeMismatchFix
        	}
        corrections match {
          case Nil => null
          case l => l.distinct.toArray
        }
      }
      case _ => null
  }

  // XXX is this code duplication? -- check scala.tools.eclipse.util.EditorUtils.getAnnotationsAtOffset
  private def getAnnotationsAtOffsetXXX(part: IEditorPart, offset: Int): List[Annotation] = {
	  import ScalaQuickFixProcessor._
	
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

  private
  def suggestImportFix(compilationUnit : ICompilationUnit, problemMessage : String) : List[IJavaCompletionProposal] = {
    /**
     * Import a type could solve several error message :
     *
     * * "not found : type  Xxxx"
     * * "not found : value Xxxx" in case of java static constant/method like Xxxx.ZZZZ or Xxxx.zzz()
     * * "not found : Xxxx" in case of new Xxxx.eee (IMO (davidB) a better suggestion is to insert (), to have new Xxxx().eeee )
     */
    def suggestImportType(missingType : String) : List[IJavaCompletionProposal] = {
      val resultCollector = new java.util.ArrayList[TypeNameMatch]
      val scope = SearchEngine.createJavaSearchScope(Array[IJavaElement](compilationUnit.getJavaProject))
      val typesToSearch = Array(missingType.toArray)
      new SearchEngine().searchAllTypeNames(null, typesToSearch, scope, new TypeNameMatchCollector(resultCollector), IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, new NullProgressMonitor)
      resultCollector map { typeFound =>
        new ImportCompletionProposal(typeFound.getFullyQualifiedName)
      } toList
    }

    return problemMessage match {
      case typeNotFoundError(missingType) => suggestImportType(missingType)
      case valueNotFoundError(missingValue) => suggestImportType(missingValue)
      case xxxxxNotFoundError(missing) => suggestImportType(missing)
      case _ => Nil
    }
  }
  private
  def suggestTypeMismatchFix(document : IDocument, problemMessage : String, location: Position) : List[IJavaCompletionProposal] = {
    // get the annotation string
    val annotationString = document.get(location.getOffset, location.getLength)
    // match problem message
    return problemMessage match {
      // extract found and required type
      case typeMismatchError(foundType, requiredType) =>
    		// utilize type mismatch computer to find quick fixes
        val replacementStringList = TypeMismatchQuickFixProcessor(foundType, requiredType, annotationString)

        // map replacements strings into expanding proposals
        replacementStringList map {
          replacementString =>
            // make markers message in form: "... =>replacement"
          	val markersMessage = annotationString + ImplicitHighlightingPresenter.DisplayStringSeparator + replacementString
          	// construct a proposal with the appropriate location          	
          	new ExpandingProposalBase(markersMessage, "Transform expression: ", location)
        }
      // no match found for the problem message
      case _ => Nil
    }
  }

}

object ScalaQuickFixProcessor {
	private def isInside(offset: Int, start: Int,end: Int): Boolean = {
		return offset == start || offset == end || (offset > start && offset < end); // make sure to handle 0-length ranges
    }
}


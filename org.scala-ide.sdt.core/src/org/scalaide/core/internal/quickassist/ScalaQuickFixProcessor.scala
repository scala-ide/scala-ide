package org.scalaide.core.internal.quickassist

import scala.tools.refactoring.implementations.AddToClass
import scala.tools.refactoring.implementations.AddToClosest
import scala.tools.refactoring.implementations.AddToObject
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.quickassist.createmethod.CreateMethodProposal
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.core.internal.quickassist.expand.ExpandingProposalBase

/**
 * Contains quick fixes that can only be applied to compiler errors. If there
 * are no compilation errors than this component doesn't anything to apply.
 */
class ScalaQuickFixProcessor extends QuickAssist with HasLogger {
  private val TypeNotFoundError = "not found: type (.*)".r
  private val ValueNotFoundError = "not found: value (.*)".r
  private val XXXXXNotFoundError = "not found: (.*)".r
  private val ValueNotAMember = "value (.*) is not a member of (.*)".r
  private val ValueNotAMemberOfObject = "value (.*) is not a member of object (.*)".r

  // regex for extracting expected and required type on type mismatch
  private val TypeMismatchError = """type mismatch;\s*found\s*: (\S*)\s*required: (.*)""".r

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val ssf = ctx.sourceFile
    val editor = JavaUI.openInEditor(ssf)
    var corrections: List[IJavaCompletionProposal] = Nil
    for (location <- ctx.problemLocations)
      for ((ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)) {
        val importFix = suggestImportFix(ssf, ann.getText)
        val createClassFix = suggestCreateClassFix(ssf, ann.getText)

        // compute all possible type mismatch quick fixes
        val document = (editor.asInstanceOf[ITextEditor]).getDocumentProvider().getDocument(editor.getEditorInput())
        val typeMismatchFix = suggestTypeMismatchFix(document, ann.getText, pos)

        val createMethodFix = suggestCreateMethodFix(ssf, ann.getText, pos)
        val changeMethodCase = suggestChangeMethodCase(ssf, ann.getText, pos)

        // concatenate lists of found quick fixes
        corrections = corrections ++
          importFix ++
          typeMismatchFix ++
          createClassFix ++
          createMethodFix ++
          changeMethodCase

      }
    corrections.distinct.asInstanceOf[Seq[BasicCompletionProposal]]
  }

  private def suggestChangeMethodCase(cu: ICompilationUnit, problemMessage : String, pos: Position): List[IJavaCompletionProposal] = {
    problemMessage match {
      case ValueNotAMember(value, className) => ChangeCaseProposal.createProposals(cu, pos, value)
      case ValueNotFoundError(member) => ChangeCaseProposal.createProposalsWithCompletion(cu, pos, member)
      case _ => List()
    }
  }

  private def suggestCreateMethodFix(compilationUnit: ICompilationUnit, problemMessage : String, pos: Position): List[IJavaCompletionProposal] = {
    val possibleMatch = problemMessage match {
      case ValueNotAMemberOfObject(member, theType) => List(CreateMethodProposal(Some(theType), member, AddToObject, compilationUnit, pos))
      case ValueNotAMember(member, theType) => List(CreateMethodProposal(Some(theType), member, AddToClass, compilationUnit, pos))
      case ValueNotFoundError(member) => List(CreateMethodProposal(None, member, AddToClosest(pos.offset), compilationUnit, pos))
      case _ => Nil
    }
    possibleMatch.filter(_.isApplicable)
  }

  private def suggestImportFix(compilationUnit : ICompilationUnit, problemMessage : String) : List[IJavaCompletionProposal] = {
    import ScalaQuickFixProcessor._

    /**
     * Import a type could solve several error message :
     *
     * * "not found : type  Xxxx"
     * * "not found : value Xxxx" in case of java static constant/method like Xxxx.ZZZZ or Xxxx.zzz()
     * * "not found : Xxxx" in case of new Xxxx.eee (IMO (davidB) a better suggestion is to insert (), to have new Xxxx().eeee )
     */
    def suggestImportType(missingType : String) : List[IJavaCompletionProposal] = {
      val typeNames = searchForTypes(compilationUnit.getJavaProject(), missingType)
      typeNames map (name => new ImportCompletionProposal(name.getFullyQualifiedName))
    }

    matchTypeNotFound(problemMessage, suggestImportType)
  }

  private def matchTypeNotFound(problemMessage: String, suggest: String => List[IJavaCompletionProposal]): List[IJavaCompletionProposal] = {
    problemMessage match {
      case TypeNotFoundError(missingType) => suggest(missingType)
      case ValueNotFoundError(missingValue) => suggest(missingValue)
      case XXXXXNotFoundError(missing) => suggest(missing)
      case _ => Nil
    }
  }

  private def suggestCreateClassFix(compilationUnit : ICompilationUnit, problemMessage : String) : List[IJavaCompletionProposal] = {
    matchTypeNotFound(problemMessage, missingType => List(CreateClassProposal(missingType, compilationUnit)))
  }

  private def suggestTypeMismatchFix(document : IDocument, problemMessage : String, location: Position) : List[IJavaCompletionProposal] = {
    // get the annotation string
    val annotationString = document.get(location.getOffset, location.getLength)
    // match problem message
    return problemMessage match {
      // extract found and required type
      case TypeMismatchError(foundType, requiredType) =>
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

private[quickassist] object ScalaQuickFixProcessor {

  def searchForTypes(project: IJavaProject, name: String): List[TypeNameMatch] = {
    val resultCollector = new java.util.ArrayList[TypeNameMatch]
    val scope = SearchEngine.createJavaSearchScope(Array[IJavaElement](project))
    val typesToSearch = Array(name.toArray)
    new SearchEngine().searchAllTypeNames(
        null,
        typesToSearch,
        scope,
        new TypeNameMatchCollector(resultCollector),
        IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
        new NullProgressMonitor)

    import scala.collection.JavaConverters._
    resultCollector.asScala.toList
  }
}

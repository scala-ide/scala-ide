package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.internal.quickassist.expand.ExpandingProposalBase
import org.scalaide.core.internal.statistics.Features.FixTypeMismatch
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter

class FixTypeMismatch extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.icu.asInstanceOf[ICompilationUnit])
    ctx.problemLocations flatMap { location =>
      val doc = editor.asInstanceOf[ITextEditor].getDocumentProvider.getDocument(editor.getEditorInput)
      suggestTypeMismatchFix(doc, location.annotation.getText, location.offset, location.length)
    }
  }

  private def suggestTypeMismatchFix(document: IDocument, problemMessage: String, offset: Int, length: Int) = {
    // get the annotation string
    val annotationString = document.get(offset, length)
    // match problem message
    problemMessage match {
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
            new ExpandingProposalBase(FixTypeMismatch, markersMessage, "Transform expression: ", offset, length)
        }
      // no match found for the problem message
      case _ => Nil
    }
  }
}

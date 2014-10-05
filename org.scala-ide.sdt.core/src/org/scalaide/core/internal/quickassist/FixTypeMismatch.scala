package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.internal.quickassist.expand.ExpandingProposalBase
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitHighlightingPresenter
import org.scalaide.util.eclipse.EditorUtils

class FixTypeMismatch extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.sourceFile)
    val assists = for {
      location <- ctx.problemLocations
      (ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)
      doc = (editor.asInstanceOf[ITextEditor]).getDocumentProvider().getDocument(editor.getEditorInput())
    } yield suggestTypeMismatchFix(doc, ann.getText, pos)

    assists.flatten
  }

  private def suggestTypeMismatchFix(document: IDocument, problemMessage: String, location: Position): Seq[BasicCompletionProposal] = {
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

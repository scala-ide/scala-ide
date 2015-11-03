package org.scalaide.refactoring.internal.extract

import org.eclipse.core.resources.IMarker
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.ui.IFileEditorInput
import org.scalaide.core.internal.statistics.Features.ExtractCode
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.util.eclipse.EditorUtils

abstract class ExtractionProposal(displayString: String, hightlightFrom: Int, highlightTo: Int, relevance: Int = 0)
  extends BasicCompletionProposal(ExtractCode, relevance, displayString) with ICompletionProposalExtension2 {

  private var markerOpt: Option[IMarker] = None

  override def selected(viewer: ITextViewer, smartToggle: Boolean): Unit = {
    markerOpt.foreach(_.delete())
    EditorUtils.doWithCurrentEditor { editor =>
      markerOpt = editor.getEditorInput() match {
        case f: IFileEditorInput =>
          val m = f.getFile().createMarker("org.scalaide.refactoring.extractionScope")
          m.setAttribute(IMarker.CHAR_START, Integer.valueOf(hightlightFrom))
          m.setAttribute(IMarker.CHAR_END, Integer.valueOf(highlightTo))
          Some(m)
        case _ => None
      }
    }
  }

  override def unselected(viewer: ITextViewer): Unit = {
    markerOpt.foreach(_.delete())
  }

  override def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int): Unit = apply(null)
  override def validate(document: IDocument, offset: Int, event: DocumentEvent): Boolean = true
}

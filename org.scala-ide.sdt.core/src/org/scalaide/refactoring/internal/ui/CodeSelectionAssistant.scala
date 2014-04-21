package org.scalaide.refactoring.internal.ui

import org.scalaide.ui.internal.editor.ISourceViewerEditor
import org.eclipse.core.resources.IMarker
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContentAssistProcessor
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.IFileEditorInput
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point

/**
 * An inline code selection tool. It opens a dropdown similar
 * to the quick assist menu which lists all code snippets.
 * When the user selects one of the snippets by pressing up or down,
 * the corresponding code is highlighted in the editor.
 *
 * This class is used for the "Extract ..." refactorings.
 */
class CodeSelectionAssistant(
  snippets: List[CodeSelectionAssistant.Snippet],
  editor: ISourceViewerEditor,
  statusMessage: Option[String] = None,
  abortCallback: () => Unit = () => ()) extends ContentAssistant {

  var snippetSelected = false

  val processor = new IContentAssistProcessor {
    def computeCompletionProposals(tv: ITextViewer, offset: Int): Array[ICompletionProposal] = {
      (for {
        s <- snippets
      } yield new ICompletionProposal with ICompletionProposalExtension2 {
        def apply(doc: IDocument) = {
          snippetSelected = true
          s.selectionCallback()
        }

        def getDisplayString(): String = s.description

        var markerOpt: Option[IMarker] = None

        def selected(viewer: ITextViewer, smartToggle: Boolean) = {
          markerOpt.foreach(_.delete())
          markerOpt = editor.getEditorInput() match {
            case f: IFileEditorInput =>
              val m = f.getFile().createMarker("org.scalaide.refactoring.codeSelection")
              m.setAttribute(IMarker.CHAR_START, Integer.valueOf(s.hightlightFrom))
              m.setAttribute(IMarker.CHAR_END, Integer.valueOf(s.highlightTo))
              Some(m)
            case _ => None
          }
        }

        def unselected(viewer: ITextViewer) = {
          markerOpt.foreach(_.delete())
        }

        def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int) = apply(null)
        def validate(document: IDocument, offset: Int, event: DocumentEvent) = true
        def getAdditionalProposalInfo(): String = null
        def getContextInformation(): IContextInformation = null
        def getImage(): Image = null
        def getSelection(document: IDocument): Point = null
      }).toArray
    }

    def computeContextInformation(x$1: org.eclipse.jface.text.ITextViewer, x$2: Int): Array[org.eclipse.jface.text.contentassist.IContextInformation] = null
    def getCompletionProposalAutoActivationCharacters(): Array[Char] = null
    def getContextInformationAutoActivationCharacters(): Array[Char] = null
    def getContextInformationValidator(): org.eclipse.jface.text.contentassist.IContextInformationValidator = null
    def getErrorMessage(): String = null
  }

  // this seems to be the correct content type, probably there
  // are further types that needs to be covered... who knows.
  val contentType = "__dftl_partition_content_type"

  setContentAssistProcessor(processor, contentType)

  val emptyInformationControlCreator =
    new IInformationControlCreator {
      def createInformationControl(parent: Shell) = {
        new DefaultInformationControl(parent);
      }
    }

  // if no InformationControlCreator is registered, getAdditionalProposalInfo
  // wont be called on proposals
  setInformationControlCreator(emptyInformationControlCreator)

  statusMessage.foreach { msg =>
    setStatusMessage(msg)
    setStatusLineVisible(true)
  }

  def show() = {
    install(editor.getViewer())
    showPossibleCompletions()
  }

  var markerOpt: Option[IMarker] = None

  def highlightSnippet(s: CodeSelectionAssistant.Snippet) = {
    // for some reasons we need to rebuild the marker every time,
    // otherwise it could not be reduced to a smaller snippet
    refreshMarker()
    markerOpt.foreach { m =>
      m.setAttribute(IMarker.CHAR_START, Integer.valueOf(s.hightlightFrom))
      m.setAttribute(IMarker.CHAR_END, Integer.valueOf(s.highlightTo))
    }
  }

  def refreshMarker() = {
    markerOpt.foreach(_.delete())
    markerOpt = editor.getEditorInput() match {
      case f: IFileEditorInput =>
        Some(f.getFile().createMarker("codeSelection"))
      case _ => None
    }
  }

  override def possibleCompletionsClosed() = {
    markerOpt.foreach(_.delete())
    super.possibleCompletionsClosed()
    if (!snippetSelected) {
      abortCallback()
    }
  }
}

object CodeSelectionAssistant {
  /**
   * Represents some code in the current editor.
   * `selectionCallback` is called after the user
   * selects the snippet and hits the enter key.
   */
  case class Snippet(
    description: String,
    hightlightFrom: Int,
    highlightTo: Int,
    selectionCallback: () => Unit)
}
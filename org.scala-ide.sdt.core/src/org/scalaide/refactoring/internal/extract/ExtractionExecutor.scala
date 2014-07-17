package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.extraction.ExtractionRefactoring

import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContentAssistProcessor
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.contentassist.IContextInformationValidator
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.PlatformUI
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.refactoring.internal.RefactoringExecutor
import org.scalaide.refactoring.internal.ScalaIdeRefactoring
import org.scalaide.util.internal.eclipse.EditorUtils

trait ExtractionExecutor extends RefactoringExecutor {
  abstract class ScalaIdeExtractionRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Extract...", file, selectionStart, selectionEnd) {
    val refactoring: ExtractionRefactoring with InteractiveScalaCompiler

    var selectedExtraction: Option[refactoring.Extraction] = None

    def refactoringParameters: refactoring.RefactoringParameters =
      selectedExtraction.get

    val initialSelection = EditorUtils.withCurrentEditor { editor =>
      Some(editor.getViewer().getSelectedRange())
    }

    def highlightExtractionSource = {
      preparationResult().right.map { pr =>
        pr.extractions.headOption.foreach { e =>
          EditorUtils.doWithCurrentEditor { editor =>
            val viewer = editor.getViewer()
            val pos = e.extractionSource.pos
            viewer.setSelectedRange(pos.start, pos.end - pos.start)
          }
        }
      }
    }

    def resetSelection() = initialSelection.map { s =>
      EditorUtils.doWithCurrentEditor { editor =>
        editor.getViewer().setSelectedRange(s.x, s.y)
      }
    }
  }

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile): ScalaIdeExtractionRefactoring

  override def perform() = {
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

    createScalaIdeRefactoringForCurrentEditorAndSelection() match {
      case Some(r: ScalaIdeExtractionRefactoring) =>
        r.highlightExtractionSource
        doWithSelectedExtraction(r) {
          case Some(e) =>
            r.selectedExtraction = Some(e)
            if (runRefactoring(r, shell))
              LocalNameOccurrences(e.abstractionName).performInlineRenaming()
          // use the refactoring wizard for displaying preparation errors
          case None =>
            r.resetSelection()
            if (r.preparationResult().isLeft)
              runRefactoring(createWizardForRefactoring(Some(r)), shell)
        }
      case _ => runRefactoring(createWizardForRefactoring(None), shell)
    }
  }

  /**
   * Runs the refactoring and returns true if the refactoring succeeded and false
   * if it failed or has been aborted.
   * If the refactoring has no wizard pages and modifies only one file,
   * we bypass the LTK refactoring. Otherwise we use the LTK to show
   * the wizard pages (at least the preview page) and to use the LTKs
   * undo functionality over several files.
   */
  def runRefactoring(refactoring: ScalaIdeExtractionRefactoring, shell: Shell): Boolean = {
    refactoring.performRefactoring() match {
      case (change: TextChange) :: Nil if refactoring.getPages.isEmpty =>
        EditorUtils.doWithCurrentEditor { editor =>
          EditorUtils.applyRefactoringChangeToEditor(change, editor)
        }
        true
      case _ =>
        val w = createWizardForRefactoring(Some(refactoring))
        if (new RefactoringWizardOpenOperation(w).run(shell, "Scala Refactoring") == IDialogConstants.OK_ID) {
          true
        } else {
          refactoring.resetSelection()
          false
        }
    }
  }

  /**
   * Opens a content assist that allows the selection
   * of a proposed extraction.
   * If the user selects an extraction and hits enter, `block` is
   * called with the selected extraction, otherwise with `None`.
   */
  def doWithSelectedExtraction(ideRefactoring: ScalaIdeExtractionRefactoring)(block: Option[ideRefactoring.refactoring.Extraction] => Unit) = {
    EditorUtils.doWithCurrentEditor { editor =>
      ideRefactoring.preparationResult().fold(
        { _ => block(None) },
        { es =>
          val proposals = es.extractions.map { e =>
            val pos = e.extractionTarget.enclosing.pos
            new ExtractionProposal(e.displayName, pos.start, pos.end) {
              def apply(doc: IDocument) = {
                block(Some(e))
              }
            }
          }

          val processor = new IContentAssistProcessor {
            def computeCompletionProposals(tv: ITextViewer, offset: Int): Array[ICompletionProposal] = {
              proposals.toArray
            }
            def computeContextInformation(tv: ITextViewer, offset: Int): Array[IContextInformation] = null
            def getCompletionProposalAutoActivationCharacters(): Array[Char] = null
            def getContextInformationAutoActivationCharacters(): Array[Char] = null
            def getContextInformationValidator(): IContextInformationValidator = null
            def getErrorMessage(): String = null
          }

          val assistant = new ContentAssistant {
            override def possibleCompletionsClosed() = {
              block(None)
            }
          }
          assistant.setContentAssistProcessor(processor, "__dftl_partition_content_type")
          assistant.setStatusMessage("Please choose an extraction")
          assistant.install(editor.getViewer())
          assistant.showPossibleCompletions()
        })
    }
  }
}
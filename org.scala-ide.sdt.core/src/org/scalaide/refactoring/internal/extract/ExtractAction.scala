package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.extraction.ExtractionRefactoring
import org.eclipse.jface.action.IAction
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.PlatformUI
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.refactoring.internal.EditorHelpers
import org.scalaide.refactoring.internal.RefactoringAction
import org.scalaide.refactoring.internal.ScalaIdeRefactoring
import org.scalaide.refactoring.internal.ui.CodeSelectionAssistant

trait ExtractAction extends RefactoringAction {
  abstract class ScalaIdeExtractionRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Extract...", file, selectionStart, selectionEnd) {
    val refactoring: ExtractionRefactoring with InteractiveScalaCompiler

    var selectedExtraction: Option[refactoring.Extraction] = None

    val preferredName = "extracted"

    lazy val proposedPlaceholderName = {
      // we simply search the CU for name collisions
      // because the name must be unique to the CU
      // in order to not break inline renaming.
      def nameCollides(n: String): Boolean =
        file.getContents().indexOfSlice(n) != -1 ||
          // if no collision in CU found, we can check the scope
          !selectedExtraction.get.extractionTarget.scope.nameCollisions(n).isEmpty

      if (!nameCollides(preferredName))
        preferredName
      else {
        (1 to Int.MaxValue).collectFirst {
          case i if !nameCollides(preferredName + i) =>
            preferredName + i
        }.get
      }
    }

    def refactoringParameters: refactoring.RefactoringParameters

    val initialSelection = EditorHelpers.withCurrentEditor { editor =>
      Some(editor.getViewer().getSelectedRange())
    }

    def highlightExtractionSource = {
      preparationResult().right.map { pr =>
        pr.extractions.headOption.foreach { e =>
          EditorHelpers.doWithCurrentEditor { editor =>
            val viewer = editor.getViewer()
            viewer.setSelectedRange(e.extractionSource.pos.start, e.extractionSource.pos.end - e.extractionSource.pos.start)
          }
        }
      }
    }

    def resetSelection() = initialSelection.map { s =>
      EditorHelpers.doWithCurrentEditor { editor =>
        editor.getViewer().setSelectedRange(s.x, s.y)
      }
    }
  }

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile): ScalaIdeExtractionRefactoring

  override def run(action: IAction) = {
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

    createScalaIdeRefactoringForCurrentEditorAndSelection() match {
      case Some(r: ScalaIdeExtractionRefactoring) =>
        r.highlightExtractionSource
        doWithSelectedExtraction(r) {
          case Some(e) =>
            r.selectedExtraction = Some(e)
            if (runRefactoring(r, shell))
              doInlineRenaming(r.proposedPlaceholderName)
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
        EditorHelpers.doWithCurrentEditor { editor =>
          EditorHelpers.applyRefactoringChangeToEditor(change, editor)
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
   * Opens a CodeSelectionAssistent that allows the selection
   * of a proposed extraction.
   * If the user selects an extraction and hits enter, `block` is
   * called with the selected extraction, otherwise with `None`.
   */
  def doWithSelectedExtraction(ideRefactoring: ScalaIdeExtractionRefactoring)(block: Option[ideRefactoring.refactoring.Extraction] => Unit) = {
    EditorHelpers.doWithCurrentEditor { editor =>
      ideRefactoring.preparationResult().fold(
        { _ => block(None) },
        { es =>
          val snippets = es.extractions.map { e =>
            CodeSelectionAssistant.Snippet(
              e.displayName,
              e.extractionTarget.enclosing.pos.start, e.extractionTarget.enclosing.pos.end,
              () => block(Some(e)))
          }

          new CodeSelectionAssistant(snippets, editor, Some("Please choose an extraction"), () => block(None))
            .show()
        })
    }
  }

  /**
   * Enters linked mode for renaming the new abstraction and its parameters (if any).
   */
  def doInlineRenaming(name: String) =
    EditorHelpers.withCurrentScalaSourceFile { file =>
      val of = new OccurrenceFinder(file)

      val nameOccurrences = of.termNameOccurrences(name)
      val paramOccurrences = of.parameterOccurrences(name)

      EditorHelpers.enterMultiLinkedModeUi(nameOccurrences :: paramOccurrences, selectFirst = true)
    }
}
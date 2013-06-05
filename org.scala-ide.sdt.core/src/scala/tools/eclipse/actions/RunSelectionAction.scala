package scala.tools.eclipse
package actions

import org.eclipse.jface.action.IAction
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.actions.ActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.IFileEditorInput
import interpreter.ReplConsoleView
import util.Utils._

class RunSelectionAction extends ActionDelegate with IWorkbenchWindowActionDelegate {
  var workbenchWindow: IWorkbenchWindow = null

  def init(window: IWorkbenchWindow) {
    workbenchWindow = window
  }

  override def run(action: IAction) {

    for (editor <- Option(workbenchWindow.getActivePage.getActiveEditor);
        input <- editor.getEditorInput.asInstanceOfOpt[IFileEditorInput];
        textEditor <- editor.asInstanceOfOpt[ITextEditor];
        selection <- textEditor.getSelectionProvider.getSelection.asInstanceOfOpt[ITextSelection])
    {
      val project = input.getFile.getProject
      var text = selection.getText

      if (text.isEmpty) {
        val provider = textEditor.getDocumentProvider
        provider.connect(input)

        try {
          val document = provider.getDocument(input)
          val lineOffset = document.getLineOffset(selection.getStartLine)
          val lineLength = document.getLineLength(selection.getStartLine)
          text = document.get(lineOffset, lineLength)
        }
        finally {
          provider.disconnect(input)
        }
      }

      if (!text.isEmpty) {
        ReplConsoleView.makeVisible(project,workbenchWindow.getActivePage).interpret(text)
      }
    }
  }
}
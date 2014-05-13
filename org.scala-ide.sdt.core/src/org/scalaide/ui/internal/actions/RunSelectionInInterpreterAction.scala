package org.scalaide.ui.internal.actions

import scala.reflect.runtime.universe

import org.eclipse.core.resources.IProject
import org.eclipse.jface.action.IAction
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.actions.ActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.ui.internal.repl.ReplConsoleView
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt

class RunSelectionInInterpreterAction extends RunSelectionAction {
  override def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String) =
    ReplConsoleView.makeVisible(project, activePage).evaluate(text)
}

abstract class RunSelectionAction extends ActionDelegate with IWorkbenchWindowActionDelegate {
  var workbenchWindow: IWorkbenchWindow = null

  def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String)

  def init(window: IWorkbenchWindow) {
    workbenchWindow = window
  }

  override def run(action: IAction) {

    for (
      editor <- Option(workbenchWindow.getActivePage.getActiveEditor);
      input <- editor.getEditorInput.asInstanceOfOpt[IFileEditorInput];
      textEditor <- editor.asInstanceOfOpt[ITextEditor];
      selection <- textEditor.getSelectionProvider.getSelection.asInstanceOfOpt[ITextSelection]
    ) {
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
        } finally {
          provider.disconnect(input)
        }
      }

      text = text.trim()
      if (!text.isEmpty) doWithSelection(project, workbenchWindow.getActivePage, text)
    }
  }
}

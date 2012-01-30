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

class RunSelectionAction extends ActionDelegate with IWorkbenchWindowActionDelegate {
  var workbenchWindow: IWorkbenchWindow = null

  def init(window: IWorkbenchWindow) {
    workbenchWindow = window
  }
     
  def doCast[T](obj: Any): Option[T] = {
    obj match {
      case t: T => Some(t)
      case _ => None
    }
  }
  
  override def run(action: IAction) {

    for (editor <- Option(workbenchWindow.getActivePage.getActiveEditor);
        input <- doCast[IFileEditorInput](editor.getEditorInput);
        textEditor <- doCast[ITextEditor](editor);
        selection <- doCast[ITextSelection](textEditor.getSelectionProvider.getSelection)) 
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
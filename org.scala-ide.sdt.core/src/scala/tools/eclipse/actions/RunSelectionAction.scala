package scala.tools.eclipse

package actions

import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.action.IAction
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.actions.ActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IEditorActionDelegate
import org.eclipse.ui.IFileEditorInput

class RunSelectionAction extends ActionDelegate with IWorkbenchWindowActionDelegate {
  var workbenchWindow: IWorkbenchWindow = null

  def init(window: IWorkbenchWindow) {
    println("Init! " + window)
    workbenchWindow = window
  }
     
  def doCast[T](obj: Any): Option[T] = {
    obj match {
      case t: T => Some(t)
      case _ => None
    }
  }
  
  override def run(action: IAction) {
    println("run: RunSelectionAction")
   
    for (editor <- Option(workbenchWindow.getActivePage.getActiveEditor);
        input <- doCast[IFileEditorInput](editor.getEditorInput);
        textEditor <- doCast[ITextEditor](editor);
        selection <- doCast[ITextSelection](textEditor.getSelectionProvider.getSelection)) 
    {
      val project = input.getFile.getProject
//      interpreter.ReplConsole.getConsole(project) foreach { console => 
//        console.insertText(selection.getText)
//      }
      
      println("Project: " + project.getName)    
      println("Selected text: " + selection.getText)
      
      val scalaProject = ScalaPlugin.plugin.getScalaProject(project)
      
      println("Result: ")
      val repl = interpreter.EclipseRepl.replForProject(scalaProject)      
      repl.interpret(selection.getText)
    }
  }
}
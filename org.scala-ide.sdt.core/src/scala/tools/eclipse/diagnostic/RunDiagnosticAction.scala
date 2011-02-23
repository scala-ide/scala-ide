package scala.tools.eclipse
package diagnostic

import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart, IWorkbenchWindowActionDelegate, IWorkbenchWindow }

import scala.tools.eclipse.ScalaPlugin.plugin
import scala.tools.eclipse.javaelements.JDTUtils


class RunDiagnosticAction extends IObjectActionDelegate with IWorkbenchWindowActionDelegate {
  private var parentWindow: IWorkbenchWindow = null
	
  override def init(window: IWorkbenchWindow) {  
    parentWindow = window
  } 
  
  def dispose = { }
  
	def selectionChanged(action: IAction, selection: ISelection) {  }
  
  def run(action: IAction) { 
    plugin check {      
      val shell = if (parentWindow == null) ScalaPlugin.getShell else parentWindow.getShell        
      new DiagnosticDialog(shell).open
    }
  }
    
  def setActivePart(action: IAction, targetPart: IWorkbenchPart) { }
}

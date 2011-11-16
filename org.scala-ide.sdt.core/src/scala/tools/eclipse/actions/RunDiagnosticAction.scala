package scala.tools.eclipse
package actions

import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart, IWorkbenchWindowActionDelegate, IWorkbenchWindow, PlatformUI }
import scala.tools.eclipse.ScalaPlugin.plugin
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.Utils

class RunDiagnosticAction extends IObjectActionDelegate with IWorkbenchWindowActionDelegate {
  private var parentWindow: IWorkbenchWindow = null
	
  val RUN_DIAGNOSTICS = "org.scala-ide.sdt.ui.runDiag.action"
  val REPORT_BUG      = "org.scala-ide.sdt.ui.reportBug.action"
  
  override def init(window: IWorkbenchWindow) {  
    parentWindow = window
  } 
  
  def dispose = { }
  
	def selectionChanged(action: IAction, selection: ISelection) {  }
  
  def run(action: IAction) { 
    Utils tryExecute {
      action.getId match {
        case RUN_DIAGNOSTICS =>
          val shell = if (parentWindow == null) ScalaPlugin.getShell else parentWindow.getShell        
          new diagnostic.DiagnosticDialog(shell).open
        case REPORT_BUG =>
          val shell = if (parentWindow == null) ScalaPlugin.getShell else parentWindow.getShell
          new diagnostic.ReportBugDialog(shell).open
        case _ => 
      }
    }
  }
    
  def setActivePart(action: IAction, targetPart: IWorkbenchPart) { }
}

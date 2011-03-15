package scala.tools.eclipse
package actions

import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart, IWorkbenchWindowActionDelegate, IWorkbenchWindow, PlatformUI }

import scala.tools.eclipse.ScalaPlugin.plugin
import scala.tools.eclipse.javaelements.JDTUtils

class RunDiagnosticAction extends IObjectActionDelegate with IWorkbenchWindowActionDelegate {
  private var parentWindow: IWorkbenchWindow = null
	
  val RUN_DIAGNOSTICS = "org.scala-ide.sdt.ui.runDiag.action"
  val REPORT_BUG      = "org.scala-ide.sdt.ui.reportBug.action"
  val SDT_TRACKER_URL = "https://jira.typesafe.com:8090"
  
  override def init(window: IWorkbenchWindow) {  
    parentWindow = window
  } 
  
  def dispose = { }
  
	def selectionChanged(action: IAction, selection: ISelection) {  }
  
  def run(action: IAction) { 
    plugin check {
      action.getId match {
        case RUN_DIAGNOSTICS =>
          val shell = if (parentWindow == null) ScalaPlugin.getShell else parentWindow.getShell        
          new diagnostic.DiagnosticDialog(shell).open
        case REPORT_BUG =>
          val browserSupport = PlatformUI.getWorkbench.getBrowserSupport
          browserSupport.getExternalBrowser.openURL(new java.net.URL(SDT_TRACKER_URL))
        case _ => 
      }
    }
  }
    
  def setActivePart(action: IAction, targetPart: IWorkbenchPart) { }
}

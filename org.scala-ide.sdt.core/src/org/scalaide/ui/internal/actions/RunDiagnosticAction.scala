package org.scalaide.ui.internal.actions

import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IObjectActionDelegate
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.IWorkbenchWindow
import org.scalaide.util.Utils
import org.scalaide.core.internal.logging.LogManager
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.scalaide.ui.internal.diagnostic
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.eclipse.EclipseUtils

class RunDiagnosticAction extends IObjectActionDelegate with IWorkbenchWindowActionDelegate {
  private var parentWindow: IWorkbenchWindow = null

  val RUN_DIAGNOSTICS = "org.scala-ide.sdt.ui.runDiag.action"
  val REPORT_BUG      = "org.scala-ide.sdt.ui.reportBug.action"
  val OPEN_LOG_FILE   = "org.scala-ide.sdt.ui.openLogFile.action"

  override def init(window: IWorkbenchWindow) {
    parentWindow = window
  }

  override def dispose = { }

  override def selectionChanged(action: IAction, selection: ISelection) {  }

  override def run(action: IAction) {
    EclipseUtils.withSafeRunner("Error occurred while trying to create diagnostic dialog.") {
      action.getId match {
        case RUN_DIAGNOSTICS =>
          val shell = if (parentWindow == null) SWTUtils.getShell else parentWindow.getShell
          new diagnostic.DiagnosticDialog(shell).open
        case REPORT_BUG =>
          val shell = if (parentWindow == null) SWTUtils.getShell else parentWindow.getShell
          new diagnostic.ReportBugDialog(shell).open
        case OPEN_LOG_FILE =>
          OpenExternalFile(LogManager.logFile).open()
        case _ =>
      }
    }
  }

  override def setActivePart(action: IAction, targetPart: IWorkbenchPart) { }
}

package scala.tools.eclipse
package refactoring

import org.eclipse.core.runtime.{IStatus, IProgressMonitor}
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.progress.UIJob
import org.eclipse.ui.PlatformUI

object ProgressHelpers {
  
  def shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell
  
  def runInUiJob(block: (IProgressMonitor, Shell) => IStatus) {
    new UIJob("Refactoring") {
      def runInUIThread(pm: IProgressMonitor): IStatus = {
        block(pm, shell)
      }
    }.schedule
  }
    
  def runInProgressDialogBlockUi(block: IProgressMonitor => Unit) {
    runInProgressDialog(block, fork = false)
  }
    
  def runInProgressDialogNonblocking(block: IProgressMonitor => Unit) {
    runInProgressDialog(block, fork = true)
  }

  private def runInProgressDialog(block: IProgressMonitor => Unit, fork: Boolean) {
    
    val runnable = new IRunnableWithProgress {
      def run(pm: IProgressMonitor) = block(pm)
    }
    
    val dialog = new ProgressMonitorDialog(PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell)
    dialog.run(fork, true /*cancelable*/, runnable)
  } 
}
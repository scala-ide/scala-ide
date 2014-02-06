package org.scalaide.refactoring.internal

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.progress.UIJob

object ProgressHelpers {

  def shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell

  def runInUiJob(block: (IProgressMonitor, Shell) => IStatus) {
    new UIJob("Refactoring") {
      def runInUIThread(pm: IProgressMonitor): IStatus = {
        block(pm, getDisplay.getActiveShell)
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

    val dialog = new ProgressMonitorDialog(shell)
    dialog.run(fork, true /*cancelable*/, runnable)
  }
}

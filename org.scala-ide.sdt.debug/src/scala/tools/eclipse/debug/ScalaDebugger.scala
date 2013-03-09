package scala.tools.eclipse.debug

import scala.tools.eclipse.ScalaPlugin

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.PlatformUI

import model.ScalaStackFrame
import model.ScalaThread

object ScalaDebugger extends ISelectionListener {

  val classIDebugModelProvider = classOf[IDebugModelProvider]

  val modelProvider = new IDebugModelProvider {
    def getModelIdentifiers() = {
      Array(ScalaDebugPlugin.id)
    }
  }

  // Members declared in org.eclipse.ui.ISelectionListener

  override def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
    // track the currently selected thread, to be able to invoke methods on the VM
    currentThread = selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement match {
          case scalaThread: ScalaThread =>
            scalaThread
          case scalaStackFrame: ScalaStackFrame =>
            scalaStackFrame.thread
          case _ =>
            null
        }
      case _ =>
        null
    }
  }

  /** Currently selected thread in the debugger UI view.
    *
    * WARNING:
    * Mind that this code is by design subject to race-condition, clients accessing this member need to handle the case where the
    * value of `currentThread` is not the expected one. Practically, this means that accesses to `currentThread` should always happen
    * within a try..catch block. Failing to do so can cause the whole debug session to shutdown for no good reasons.
    */
  @volatile var currentThread: ScalaThread = null

  def init() {
    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }
}

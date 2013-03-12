package scala.tools.eclipse.debug

import scala.tools.eclipse.ScalaPlugin
import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.PlatformUI
import model.ScalaStackFrame
import model.ScalaThread
import scala.tools.eclipse.debug.model.ScalaObjectReference
import scala.tools.eclipse.logging.HasLogger

object ScalaDebugger extends ISelectionListener with HasLogger {

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
  @volatile private var currentThread: ScalaThread = null
  
  def currentThreadOrFindFirstSuspendedThread(objRef: ScalaObjectReference): ScalaThread = {
    if(currentThread == null) {
     logger.info("`currentThread` is null. Now looking for first suspended thread...")
     val threads = objRef.getDebugTarget.getThreads
     val suspendedThreads = threads collect { case t: ScalaThread if t.isSuspended => t}
     if(suspendedThreads.isEmpty) {
       logger.error("Could not find a suspended thread. This is a bug, please file a bug report at " + ScalaPlugin.IssueTracker)
       null
     }
     else {
       if(suspendedThreads.length > 1) logger.info {
         "There is more than one suspended thread. Using the first in the list. If you experience any issue during the " +
         "current debug session, please file a bug report at " + ScalaPlugin.IssueTracker + " and make sure to mention this " + 
         "message in the ticket's description."
       }
       suspendedThreads(0)
     }
    }
    else currentThread
  }

  def init() {
    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }
}

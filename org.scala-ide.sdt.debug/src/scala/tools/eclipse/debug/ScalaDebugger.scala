package scala.tools.eclipse.debug

import scala.tools.eclipse.ScalaPlugin

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.PlatformUI

import model.ScalaStackFrame
import model.ScalaThread

/**
 * A generic message to inform that an actor should terminates.
 * An equivalent to Akka PoisonPill
 */
object ActorExit

/**
 * A debug message used to wait until all required messages have been processed
 */
object ActorDebug

object ScalaDebugger extends ISelectionListener {

  val classIDebugModelProvider = classOf[IDebugModelProvider]

  val modelProvider = new IDebugModelProvider {
    def getModelIdentifiers() = {
      Array(modelId)
    }
  }

  val modelId = "org.scala-ide.sdt.debug"


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

  // ----

  @volatile var currentThread: ScalaThread = null

  def init() {
    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }

}

package scala.tools.eclipse.debug

import scala.collection.mutable.Buffer
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.debug.core.{IDebugEventSetListener, DebugPlugin, DebugEvent}
import org.eclipse.debug.core.model.{IDebugModelProvider, DebugElement}
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.{PlatformUI, ISelectionListener}

import model.{ScalaThread, ScalaStackFrame, ScalaDebugTarget}

/**
 * A generic message to inform that an actor should terminates
 */
object ActorExit

/**
 * A debug message used to wait until all required messages have been processed
 */
object ActorDebug

object ScalaDebugger extends ISelectionListener with HasLogger {

  val classIDebugModelProvider = classOf[IDebugModelProvider]

  val modelProvider = new IDebugModelProvider {
    def getModelIdentifiers() = {
      Array(modelId)
    }
  }

  val modelId = "org.scala-ide.sdt.debug"


  // Members declared in org.eclipse.ui.ISelectionListener

  def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
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

  var currentThread: ScalaThread = null

  def init() {
    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }

}

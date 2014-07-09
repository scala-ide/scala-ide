package org.scalaide.debug.internal

import org.scalaide.core.ScalaPlugin

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.debug.internal.ui.contexts.DebugContextManager
import org.eclipse.debug.ui.contexts.DebugContextEvent
import org.eclipse.debug.ui.contexts.IDebugContextListener
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import scala.util.Try

import model.ScalaStackFrame
import model.ScalaThread

object ScalaDebugger {

  val classIDebugModelProvider = classOf[IDebugModelProvider]

  val modelProvider = new IDebugModelProvider {
    def getModelIdentifiers() = {
      Array(ScalaDebugPlugin.id)
    }
  }

  @volatile private var _currentThread: ScalaThread = null
  @volatile private var _currentStackFrame: ScalaStackFrame = null

  /**
   * Currently selected thread & stack frame in the debugger UI view.
   *
   * WARNING:
   * Mind that this code is by design subject to race-condition, clients accessing these members need to handle the case where the
   * values of `currentThread` & `currentStackFrame` are not the expected ones. Practically, this means that accesses to these members
   * should always happen within a try..catch block. Failing to do so can cause the whole debug session to shutdown for no good reasons.
   */
  def currentThread = _currentThread
  def currentStackFrame = _currentStackFrame

  def updateCurrentThreadAndStackFrame(selection: ISelection) {
    val (newThread, newStackFrame) = selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement match {
          case scalaThread: ScalaThread =>
            (scalaThread, Try(scalaThread.getTopStackFrame.asInstanceOf[ScalaStackFrame]) getOrElse null)
          case scalaStackFrame: ScalaStackFrame =>
            (scalaStackFrame.thread, scalaStackFrame)
          case _ =>
            (null, null)
        }
      case _ =>
        (null, null)
    }

    _currentThread = newThread
    _currentStackFrame = newStackFrame
  }

  def init() {
    if (!ScalaPlugin.plugin.headlessMode) {
      ScalaDebuggerContextListener.register()
    }
  }

  /** `IDebugContextListener` is part of the Eclipse UI code, by extending it in a different
   *  object, it will not be loaded as soon as `ScalaDebugger` is used.
   *  This allow to use `ScalaDebugger` even if the application is launched in `headless` mode, like while running tests.
   */
  private object ScalaDebuggerContextListener extends IDebugContextListener {

    def register() {
      DebugContextManager.getDefault().addDebugContextListener(this)
    }

    override def debugContextChanged(event: DebugContextEvent) {
      ScalaDebugger.updateCurrentThreadAndStackFrame(event.getContext())
    }
  }
}

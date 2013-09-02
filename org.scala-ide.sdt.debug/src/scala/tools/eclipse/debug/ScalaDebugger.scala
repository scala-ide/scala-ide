package scala.tools.eclipse.debug

import scala.tools.eclipse.ScalaPlugin

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.debug.internal.ui.contexts.DebugContextManager
import org.eclipse.debug.ui.contexts.DebugContextEvent
import org.eclipse.debug.ui.contexts.IDebugContextListener
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection

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

  /**
   * Currently selected thread in the debugger UI view.
   *
   * WARNING:
   * Mind that this code is by design subject to race-condition, clients accessing this member need to handle the case where the
   * value of `currentThread` is not the expected one. Practically, this means that accesses to `currentThread` should always happen
   * within a try..catch block. Failing to do so can cause the whole debug session to shutdown for no good reasons.
   */
  def currentThread = _currentThread

  private[debug] def updateCurrentThread(selection: ISelection) {
    _currentThread = selection match {
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
      ScalaDebugger.updateCurrentThread(event.getContext())
    }
  }
}

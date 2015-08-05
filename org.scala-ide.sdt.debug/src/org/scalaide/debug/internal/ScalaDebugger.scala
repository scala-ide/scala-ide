package org.scalaide.debug.internal

import org.eclipse.debug.core.model.IDebugModelProvider
import org.eclipse.debug.internal.ui.contexts.DebugContextManager
import org.eclipse.debug.ui.contexts.DebugContextEvent
import org.eclipse.debug.ui.contexts.IDebugContextListener
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection

import org.scalaide.core.IScalaPlugin

import com.sun.jdi.StackFrame

import model.ScalaStackFrame
import model.ScalaThread

object ScalaDebugger {

  val classIDebugModelProvider = classOf[IDebugModelProvider]

  val modelProvider = new IDebugModelProvider {
    override def getModelIdentifiers() = {
      Array(ScalaDebugPlugin.id)
    }
  }

  @volatile private var _currentThread: ScalaThread = null
  @volatile private var _currentStackFrame: ScalaStackFrame = null
  @volatile private var _currentFrameIndex: Int = 0

  /**
   * Currently selected thread & stack frame in the debugger UI view.
   *
   * WARNING:
   * Mind that this code is by design subject to race-condition, clients accessing these members need to handle the case where the
   * values of `currentThread` & `currentStackFrame` are not the expected ones. Practically, this means that accesses to these members
   * should always happen within a try..catch block. Failing to do so can cause the whole debug session to shutdown for no good reasons.
   */
  def currentThread: ScalaThread = _currentThread
  def currentStackFrame: ScalaStackFrame = _currentStackFrame
  def currentFrame(): Option[StackFrame] = Option(currentThread).map(_.threadRef.frame(_currentFrameIndex))

  private[debug] def updateCurrentThread(selection: ISelection): Unit = {
    def setValues(thread: ScalaThread, frame: ScalaStackFrame, frameIndex: Int = 0): Unit = {
      _currentThread = thread
      _currentStackFrame = frame
      _currentFrameIndex = frameIndex
    }

    selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement match {
          case scalaThread: ScalaThread =>
            setValues(thread = scalaThread, frame = scalaThread.getTopStackFrame, frameIndex = 0)
          case scalaStackFrame: ScalaStackFrame =>
            setValues(thread = scalaStackFrame.thread, frame = scalaStackFrame, frameIndex = scalaStackFrame.index)
          case _ =>
            setValues(thread = null, frame = null)
        }
      case _ =>
        setValues(thread = null, frame = null)
    }
  }

  def init(): Unit = {
    if (!IScalaPlugin().headlessMode) {
      ScalaDebuggerContextListener.register()
    }
  }

  /**
   * `IDebugContextListener` is part of the Eclipse UI code, by extending it in a different
   *  object, it will not be loaded as soon as `ScalaDebugger` is used.
   *  This allow to use `ScalaDebugger` even if the application is launched in `headless` mode, like while running tests.
   */
  private object ScalaDebuggerContextListener extends IDebugContextListener {

    def register(): Unit = {
      DebugContextManager.getDefault().addDebugContextListener(this)
    }

    override def debugContextChanged(event: DebugContextEvent): Unit = {
      ScalaDebugger.updateCurrentThread(event.getContext())
    }
  }

}

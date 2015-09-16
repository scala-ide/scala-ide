package org.scalaide.util.ui

import org.eclipse.swt.widgets.Display
import org.scalaide.core.IScalaPlugin
import org.scalaide.util.internal.ui.UIThread

object DisplayThread extends UIThread {

  /**
   * Asynchronously run `f` on the UI thread.
   *
   * If `IScalaPlugin().headlessMode` is set, nothing is done.
   */
  override def asyncExec(f: => Unit): Unit = if (!IScalaPlugin().headlessMode) {
    Display.getDefault asyncExec new Runnable {
      override def run(): Unit = { f }
    }
  }

  /**
   * Synchronously run `f` on the UI thread.
   *
   * If `IScalaPlugin().headlessMode` is set, nothing is done.
   */
  override def syncExec(f: => Unit): Unit = if (!IScalaPlugin().headlessMode) {
    Display.getDefault syncExec new Runnable {
      override def run(): Unit = { f }
    }
  }

  private[scalaide] override def get: Thread = Display.getDefault.getThread()
}

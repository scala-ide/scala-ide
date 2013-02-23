package scala.tools.eclipse.ui

import org.eclipse.swt.widgets.Display

object DisplayThread extends UIThread {

  /** Asynchronously run `f` on the UI thread.  */
  override def asyncExec(f: => Unit) {
    Display.getDefault asyncExec new Runnable {
      override def run() { f }
    }
  }

  /** Synchronously run `f` on the UI thread.  */
  override def syncExec(f: => Unit) {
    Display.getDefault syncExec new Runnable {
      override def run() { f }
    }
  }
  
  override def get: Thread = Display.getDefault.getThread()
}
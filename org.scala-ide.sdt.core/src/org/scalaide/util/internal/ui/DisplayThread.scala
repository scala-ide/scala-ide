package org.scalaide.util.internal.ui

import org.eclipse.swt.widgets.Display

object DisplayThread extends UIThread with org.scalaide.util.DisplayThread {

  override def asyncExec(f: => Unit) {
    Display.getDefault asyncExec new Runnable {
      override def run() { f }
    }
  }

  override def syncExec(f: => Unit) {
    Display.getDefault syncExec new Runnable {
      override def run() { f }
    }
  }

  override def get: Thread = Display.getDefault.getThread()
}

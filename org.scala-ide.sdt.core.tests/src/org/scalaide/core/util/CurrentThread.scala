package scala.tools.eclipse.util

import scala.tools.eclipse.ui.UIThread

object CurrentThread extends UIThread {
  override def asyncExec(f: => Unit): Unit = syncExec(f)

  override def syncExec(f: => Unit): Unit = f

  override def get: Thread = Thread.currentThread()
}
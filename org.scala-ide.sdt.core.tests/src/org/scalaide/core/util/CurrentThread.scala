package org.scalaide.core.util

import org.scalaide.util.internal.ui.UIThread

object CurrentThread extends UIThread {
  override def asyncExec(f: => Unit): Unit = syncExec(f)

  override def syncExec(f: => Unit): Unit = f

  override def get: Thread = Thread.currentThread()
}
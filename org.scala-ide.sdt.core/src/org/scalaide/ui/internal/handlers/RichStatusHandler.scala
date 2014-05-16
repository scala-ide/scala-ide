package org.scalaide.ui.internal.handlers

import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.IStatusHandler
import org.scalaide.util.internal.ui.DisplayThread
import org.scalaide.core.ScalaPlugin
import org.eclipse.ui.PlatformUI

trait RichStatusHandler extends IStatusHandler {

  final def handleStatus(status: IStatus, source: Object): Object = {
    if (!ScalaPlugin.plugin.headlessMode) {
      val display = PlatformUI.getWorkbench().getDisplay();
      if (PlatformUI.isWorkbenchRunning() && display != null && !display.isDisposed()) {
        // the correct display thread and spawn to it if not.
        if (Thread.currentThread().equals(display.getThread())) {
          // The current thread is the display thread, execute synchronously
          doHandleStatus(status, source);
        } else {
          DisplayThread.syncExec(doHandleStatus(status, source))
        }
      }
    }
    null
  }

  protected def doHandleStatus(status: IStatus, source: Object): Unit

}
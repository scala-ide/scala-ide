package org.scalaide.ui.internal.handlers

import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.core.runtime.IStatus
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.{ MessageDialog => MD }
import org.scalaide.core.IScalaPlugin
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.scalaide.util.eclipse.SWTUtils

object CompilerLaunchErrorHandler {

  /**
   * Status code indicating there was an error at launch time
   * Linked to ScalaLaunchDelegate via our statusHandlers extension (see plugin.xml)
   */
  final val STATUS_CODE_LAUNCH_ERROR = 1003

}

class CompilerLaunchErrorHandler extends RichStatusHandler {

  def doHandleStatus(status: IStatus, source: Object) = {
    val continue = source.asInstanceOfOpt[AtomicBoolean]
    if (continue.isDefined) {
      val continueLaunch = continue.get

      if (!IScalaPlugin().headlessMode) {
        val dialog = new MD(
          SWTUtils.getShell,
          "Detected problem",
          null,
          status.getMessage + " Continue launch?",
          MD.WARNING,
          Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL),
          1)
        dialog.open()
        val returnValue = dialog.getReturnCode()
        continueLaunch.set(returnValue == IDialogConstants.OK_ID)
      }
    }
  }

}

package org.scalaide.ui.internal.handlers

import org.eclipse.debug.core.IStatusHandler
import scala.reflect.runtime.universe
import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.IStatusHandler
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.{ MessageDialog => MD, MessageDialogWithToggle}
import org.scalaide.core.IScalaPlugin
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.UtilsImplicits.withAsInstanceOfOpt
import java.util.concurrent.atomic.AtomicBoolean
import org.scalaide.util.DisplayThread
import org.scalaide.util.SWTUtils

object CompilerLaunchErrorHandler {

  /**
   * Status code indicating there was an error at launch time
   *  Linked to ScalaLaunchDelegate via our statusHandlers extension (see plugin.xml)
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

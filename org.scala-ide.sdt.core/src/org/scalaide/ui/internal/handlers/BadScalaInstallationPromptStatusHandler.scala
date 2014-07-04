package org.scalaide.ui.internal.handlers

import scala.reflect.runtime.universe
import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.IStatusHandler
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.{MessageDialog => MD}
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import scala.concurrent.Promise
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.ui.UIStatusesConverter
import org.scalaide.ui.internal.project.ScalaInstallationChoiceUIProviders
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.util.internal.CompilerUtils
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.project.ScalaInstallationChoiceListDialog
import org.scalaide.util.internal.Utils

object BadScalaInstallationPromptStatusHandler {

  /** Status code indicating there was a previous scala library detected
   *  on classpath. Linked to BadScalaInstallationPromptStatusHandler
   *  via our statusHandlers extension (see plugin.xml)
   */
  final val STATUS_CODE_PREV_CLASSPATH = 1001

}

class BadScalaInstallationPromptStatusHandler extends RichStatusHandler with HasLogger {

  def doHandleStatus(status: IStatus, source: Object) = {
    val (scalaProject, continuation)  = source match {
      case (p: ScalaProject, c: Promise[() => Unit]) => (Some(p), Some(c))
      case (_, c: Promise[()=> Unit]) => (None, Some(c))
      case _ => (None, None)
    }
    val shell = ScalaPlugin.getShell

    val title = "Wrong Scala library version detected in this project"
    val projectName = scalaProject map ( _.underlying.getName()) getOrElse("")
    val message = status.getMessage()

    if (scalaProject.isDefined) {
      val project = scalaProject.get
      val severity = UIStatusesConverter.MessageDialogOfIStatus(status.getSeverity())
      val dialog = new MD(
        shell,
        title,
        null,
        message,
        severity,
        Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL),
        1)
      dialog.open()
      val buttonId = dialog.getReturnCode()
      if (buttonId == IDialogConstants.OK_ID) {
        val installationChoiceList = ScalaInstallationChoiceListDialog(shell, project)
        val rCode = installationChoiceList.open()
        val res = installationChoiceList.getInstallationChoice()
        if (res.isDefined) continuation.get trySuccess { () => Utils.tryExecute(project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, res.get.toString())) }
        else continuation.get trySuccess { () => }
      } else continuation.get trySuccess { () => Utils.tryExecute(project.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, ScalaInstallationChoice(ScalaPlugin.plugin.scalaVer).toString())) }
    } else continuation map { _ failure (new IllegalArgumentException) }
  }

}

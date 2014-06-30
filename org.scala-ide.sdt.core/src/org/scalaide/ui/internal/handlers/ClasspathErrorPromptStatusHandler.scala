package org.scalaide.ui.internal.handlers

import scala.reflect.runtime.universe
import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.IStatusHandler
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.{MessageDialog => MD}
import org.eclipse.ui.dialogs.PreferencesUtil
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt
import org.scalaide.util.internal.Utils
import org.scalaide.util.internal.CompilerUtils
import scala.concurrent.Promise
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.ui.internal.preferences.CompilerSettings

object ClasspathErrorPromptStatusHandler {

  /** Status code indicating there was a previous scala library detected
   *  on classpath. Linked to ClassPathErrorPromptStatusHandler
   *  via our statusHandlers extension (see plugin.xml)
   */
  final val STATUS_CODE_PREV_CLASSPATH = 1001

}

class ClasspathErrorPromptStatusHandler extends RichStatusHandler {

  def doHandleStatus(status: IStatus, source: Object) = {
    val (scalaProject, continuation)  = source match {
      case (p: ScalaProject, c: Promise[() => Unit]) => (Some(p), Some(c))
      case (_, c: Promise[()=> Unit]) => (None, Some(c))
      case _ => (None, None)
    }
    val shell = ScalaPlugin.getShell

    val title = "Prior Scala library version detected in this project"
    val expectedVer = ScalaPlugin.plugin.scalaVer.unparse
    val projectName = scalaProject map ( _.underlying.getName()) getOrElse("")
    val message = s"The version of scala library found in the build path of $projectName is prior to the one provided by scala IDE. We rather expected: $expectedVer Turn on the source level flags for this specific project ?"

    val previousScalaVer = CompilerUtils.previousShortString(ScalaPlugin.plugin.scalaVer)

    if (scalaProject.isDefined) {
      val project = scalaProject.get

      val dialog = new MD(
        shell,
        title,
        null,
        message,
        MD.WARNING,
        Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL),
        1)
      dialog.open()
      val buttonId = dialog.getReturnCode()
      if (buttonId == IDialogConstants.OK_ID) continuation.get trySuccess {() => Utils.tryExecute(project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), "Classpath check dialog tasked with restoring compatibility")) }
      else continuation.get trySuccess { () => }
    } else continuation map { _ failure (new IllegalArgumentException) }
  }

}

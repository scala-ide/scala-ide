package org.scalaide.ui.internal.diagnostic

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

object ClasspathErrorPromptStatusHandler {

  /** Status code indicating there was a previous scala library detected
   *  on classpath. Linked to ClassPathErrorPromptStatusHandler
   *  via our statusHandlers extension (see plugin.xml)
   */
  final val STATUS_CODE_PREV_CLASSPATH = 1001;

}

class ClasspathErrorPromptStatusHandler extends IStatusHandler {

  def handleStatus(status: IStatus, source: Object): Object = {
    val scalaProject = source.asInstanceOfOpt[ScalaProject]
    val shell = ScalaPlugin.getShell

    val title = "Prior Scala library version detected in this project"
    val expectedVer = ScalaPlugin.plugin.scalaVer.unparse
    val message = s"The version of scala library found in the build path is prior to the one provided by scala IDE. We expected: $expectedVer Turn on the -Xsource flag for this specific project ?"

    val previousScalaVer = ScalaPlugin.plugin.scalaVer match {
      case ScalaPlugin.plugin.ShortScalaVersion(major, minor) => {
        // This is technically incorrect for an epoch change, but the Xsource flag won't be enough to cover for that anyway
        val lesserMinor = minor - 1
        f"$major%d.$lesserMinor%2d"
      }
      case _ => "none"
    }

    val project = scalaProject.get

    def toggleProjectSpecificSettingsAndSetXsource() = {
      project.projectSpecificStorage.setValue(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, true)
      project.projectSpecificStorage.save()
      val additionalParams = project.storage.getString(CompilerSettings.ADDITIONAL_PARAMS)
      project.storage.setValue(CompilerSettings.ADDITIONAL_PARAMS, additionalParams + " -Xsource:" + previousScalaVer)
    }

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
    if (buttonId == IDialogConstants.OK_ID) toggleProjectSpecificSettingsAndSetXsource()

    return null

  }

}
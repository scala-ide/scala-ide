package org.scalaide.ui.internal.project

import org.scalaide.core.internal.project.ScalaInstallation
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.eclipse.swt.widgets.Shell
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt

class ScalaInstallationChoiceListDialog(shell: Shell, project:ScalaProject, labeler:ScalaInstallationChoiceUIProviders#LabelProvider) extends ElementListSelectionDialog(shell, labeler) {
  import scala.collection.JavaConverters._

  val previousVersionChoice = PartialFunction.condOpt(ScalaInstallation.platformInstallation.version) {case ShortScalaVersion(major, minor) => ScalaInstallationChoice(ScalaVersion(f"$major%d.${minor-1}%d"))}
  def previousVersionPrepender(l:List[ScalaInstallationChoice]) = previousVersionChoice.fold(l)(s => s :: l)
  val inputs =  ScalaInstallationChoice(ScalaPlugin.plugin.scalaVer) :: previousVersionPrepender(ScalaInstallation.availableInstallations.map(si => ScalaInstallationChoice(si)))

  setElements(inputs.toArray)
  setTitle("Scala Installations")
  setMessage("Choose a Scala Installation")
  setInitialElementSelections(List(project.getDesiredInstallationChoice()).asJava)
  setMultipleSelection(false)

  def getInstallationChoice(): Option[ScalaInstallationChoice] = {
    val res = getResult()
    if (res != null && !res.isEmpty) res(0).asInstanceOfOpt[ScalaInstallationChoice]
    else None
  }

}

object ScalaInstallationChoiceListDialog{

  def apply(shell: Shell, project:ScalaProject): ScalaInstallationChoiceListDialog = {
    val choices = new ScalaInstallationChoiceUIProviders() { override def itemTitle = "Scala Installations"}
    new ScalaInstallationChoiceListDialog(shell, project, new choices.LabelProvider())
  }
}
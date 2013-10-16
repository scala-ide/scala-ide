package org.scalaide.sbt.core.wizard

import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT._
import java.io.File
import org.scalaide.sbt.core.SbtRemoteProcess
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.layout.GridLayout

class ImportSbtBuildProjectSelectionWizardPage extends WizardPage("projectSelection", "Sbt build projects", null) {

  var nameLabel: Label = _

  override def createControl(parent: Composite) {
    val topLevel = new Composite(parent, NONE)
    topLevel.setLayout(new GridLayout(2, false))

    nameLabel = new Label(topLevel, BORDER)
    nameLabel.setText("fetching...")

    setControl(topLevel)

    setPageComplete(false)

  }

  override def setVisible(visible: Boolean) {
    super.setVisible(visible)
    if (visible) {
      val buildRoot = new File(getPreviousPage().asInstanceOf[ImportSbtBuildRootSelectionWizardPage].buildRootEditor.getStringValue())
      nameLabel.setText(SbtRemoteProcess.getCachedProcessFor(buildRoot).getName())
      getControl().pack()
    }
  }

}
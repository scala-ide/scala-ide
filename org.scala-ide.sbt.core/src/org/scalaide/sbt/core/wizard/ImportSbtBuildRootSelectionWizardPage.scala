package org.scalaide.sbt.core.wizard

import java.io.File
import scala.tools.eclipse.util.SWTUtils.fnToPropertyChangeListener
import org.eclipse.jface.preference.DirectoryFieldEditor
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.SWT.NONE
import org.eclipse.swt.widgets.Composite
import com.typesafe.sbtrc.io.SbtVersionUtil

object ImportSbtBuildRootSelectionWizardPage {

  private class SbtBuildRootFieldEditor(parent: Composite) extends DirectoryFieldEditor("buildRoot", "Sbt Build Root:", parent) {

    setErrorMessage("Unable to find 'sbt.version' in project/build.properties file.")

    override def setValidateStrategy(strategy: Int) {
      // shortcut this feature, we always want VALIDATE_ON_KEY_STROKE 
      super.setValidateStrategy(StringFieldEditor.VALIDATE_ON_KEY_STROKE)
    }

    override def doCheckState(): Boolean = {
      if (super.doCheckState()) {
        SbtVersionUtil.findProjectSbtVersion(new File(getStringValue().trim())).isDefined
      } else {
        false
      }
    }
  }

}

class ImportSbtBuildRootSelectionWizardPage extends WizardPage("rootSelection", "Sbt build root", null) {

  import ImportSbtBuildRootSelectionWizardPage._

  var buildRootEditor: StringFieldEditor = _

  override def createControl(parent: Composite) {

    val topLevel = new Composite(parent, NONE)

    buildRootEditor = new SbtBuildRootFieldEditor(topLevel)
    buildRootEditor.setPage(this)
    buildRootEditor.setPropertyChangeListener {
      event: PropertyChangeEvent =>
        checkState()
    }

    setControl(topLevel)

    setPageComplete(false)
  }

  private def checkState() {
    setPageComplete(buildRootEditor.isValid())
  }

}
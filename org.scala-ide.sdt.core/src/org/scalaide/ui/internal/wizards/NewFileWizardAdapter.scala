package org.scalaide.ui.internal.wizards

import org.eclipse.core.runtime.IConfigurationElement
import org.eclipse.jface.wizard.WizardPage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.internal.dialogs.WorkbenchWizardElement
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard

/**
 * Adapter to the `INewWizard` interface for the `NewFileWizard`.
 *
 * @constructor Takes the id of the extension which invoked the wizard. The
 * template provided by this extension is used as the default selection of the
 * template selection choices.
 */
final class NewFileWizardAdapter(fileCreatorId: String) extends BasicNewResourceWizard { self =>

  setWindowTitle("New File Wizard")

  private val page = new WizardPage("create-file-page") with NewFileWizard {

    setTitle("Create New File")
    setDescription("Creates a new file based on the contents of a template")

    override def shell = Display.getCurrent().getActiveShell()
    override def fileCreatorId = self.fileCreatorId

    override def createControl(parent: Composite): Unit = {
      setControl(createContents(parent))
    }

    override def enableOkButton(b: Boolean): Unit = {
      setPageComplete(b)
    }

    override def dispose(): Unit = {
      super[WizardPage].dispose()
      super[NewFileWizard].dispose()
    }
  }

  override def performFinish(): Boolean = {
    page.okPressed()
    true
  }

  override def addPages(): Unit = {
    addPage(page)
  }
}

/**
 * Adapter element to access the `NewFileWizard`. The purpose of this class is
 * to be put into the wizard registry of Eclipse in order to call elements of
 * the `fileCreator` extension point.
 */
final class ScalaWizardElement(e: IConfigurationElement) extends WorkbenchWizardElement(e) {

  override def getDescription(): String =
    e.getAttribute(IWorkbenchRegistryConstants.TAG_DESCRIPTION)

  override def createExecutableExtension(): AnyRef =
    new NewFileWizardAdapter(getId())

}

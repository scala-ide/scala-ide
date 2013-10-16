package org.scalaide.sbt.core.wizard

import org.eclipse.jface.wizard.Wizard
import org.eclipse.ui.IImportWizard
import org.eclipse.ui.IWorkbench
import org.eclipse.jface.viewers.IStructuredSelection

class ImportSbtBuildWizard extends Wizard with IImportWizard {

  // Members declared in org.eclipse.ui.IWorkbenchWizard
  override def init(workbench: IWorkbench, selection: IStructuredSelection): Unit = {
    // nothing to do
  }

  // Members declared in org.eclipse.jface.wizard.Wizard
  override def performFinish(): Boolean = ???

  override def addPages() {
    super.addPages()
    addPage(new ImportSbtBuildRootSelectionWizardPage())
    addPage(new ImportSbtBuildProjectSelectionWizardPage())
  }

}
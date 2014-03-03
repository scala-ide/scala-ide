package org.scalaide.sbt.ui.wizard

import org.eclipse.jface.wizard.Wizard
import org.eclipse.ui.IImportWizard
import org.eclipse.ui.IWorkbench
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.internal.wizards.datatransfer.DataTransferMessages
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin

class ProjectBuildImportWizard extends Wizard with IImportWizard {

  private var mainPage: ProjectsImportPage = _
  @volatile
  private var currentSelection: IStructuredSelection = _

  override def init(workbench: IWorkbench, selection: IStructuredSelection): Unit = {
    setWindowTitle(DataTransferMessages.DataTransfer_importTitle)
    setDefaultPageImageDescriptor(IDEWorkbenchPlugin.getIDEImageDescriptor("wizban/importproj_wiz.png"))
    this.currentSelection = selection
  }

  override def performFinish(): Boolean = mainPage.createProjects()

  override def addPages() {
    super.addPages()
    mainPage = new ProjectsImportPage(currentSelection)
    addPage(mainPage)
  }

  // to have a progress bar, instead of busy cursor only.
  override def needsProgressMonitor = true
}
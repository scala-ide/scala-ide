/*
 * Copyright 2010 LAMP/EPFL
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.ui.wizards.NewElementWizard

import org.eclipse.jface.viewers.IStructuredSelection

abstract class AbstractNewElementWizard(protected val wizardPage: AbstractNewElementWizardPage)
  extends NewElementWizard {

  setWindowTitle("Create a new Scala " + wizardPage.declarationType)

  override def addPages(): Unit = {
    super.addPages()
    addPage(wizardPage)
    wizardPage.init(getSelection())
  }

  override def performFinish(): Boolean = {

    val requestAccepted = super.performFinish()

    if(requestAccepted) {
      val resource = wizardPage.getModifiedResource()
      if(resource != null) {
        selectAndReveal(resource)
        openResource(resource.asInstanceOf[IFile])
      }
    }
    requestAccepted
  }

  override protected def canRunForked(): Boolean =
    !wizardPage.isEnclosingTypeSelected()

  def getCreatedElement(): IJavaElement =
    wizardPage.getCreatedType

  override protected def finishPage(monitor: IProgressMonitor): Unit =
    wizardPage.createType(monitor)
}

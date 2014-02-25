package org.scalaide.core.internal.quickfix

import org.scalaide.ui.internal.ScalaImages
import org.scalaide.core.completion.RelevanceValues
import org.scalaide.ui.internal.wizards.NewClassWizard
import org.scalaide.ui.internal.wizards.NewClassWizardPage
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.window.Window
import org.eclipse.jface.wizard.WizardDialog

case class CreateClassProposal(className: String, compilationUnit: ICompilationUnit)
  extends BasicCompletionProposal(
    relevance = RelevanceValues.CreateClassProposal,
    displayString = s"Create class '$className'",
    image = ScalaImages.NEW_CLASS.createImage()) {

  override def apply(document: IDocument): Unit = {
    val page = new NewClassWizardPage
    val wizard = new NewClassWizard(page)
    wizard.init(JavaPlugin.getDefault().getWorkbench(), new StructuredSelection(compilationUnit))

    val dialog = new WizardDialog(JavaPlugin.getActiveWorkbenchShell(), wizard)
    dialog.create()

    // must go after dialog.create since create calls page.createControl which
    // calls createTypeNameControls which actually makes the text field
    page.setTypeName(className, /*canBeModified = */ false)

    def existsInDifferentPackage: Boolean = {
      val existingClassPackage = compilationUnit.getPackageDeclarations().head.getElementName
      val newClassPackage = page.getPackageText()
      // can't import things from the default package, SLS 9.2
      val canBeImported = !page.isDefaultPackage
      existingClassPackage != newClassPackage && canBeImported
    }

    if (dialog.open() == Window.OK && wizard.getCreatedElement != null && existsInDifferentPackage) {
      ImportCompletionProposal(page.getFullyQualifiedName).apply(document)
    }
  }

}

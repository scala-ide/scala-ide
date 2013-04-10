package scala.tools.eclipse.quickfix

import scala.tools.eclipse.ScalaImages
import scala.tools.eclipse.wizards.NewClassWizard
import scala.tools.eclipse.wizards.NewClassWizardPage

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.window.Window
import org.eclipse.jface.wizard.WizardDialog

case class CreateClassProposal(className: String, compilationUnit: ICompilationUnit)
  extends BasicCompletionProposal(
    relevance = 90, //relevance should be less than ImportCompletionProposal since import quick fix should be first
    displayString = s"Create class '$className'",
    image = ScalaImages.NEW_CLASS.createImage()) {

  override def apply(document: IDocument): Unit = {
    val page = new NewClassWizardPage
    val wizard = new NewClassWizard(page)
    wizard.init(JavaPlugin.getDefault().getWorkbench(), new StructuredSelection(compilationUnit))
    val dialog = new WizardDialog(JavaPlugin.getActiveWorkbenchShell(), wizard)
    dialog.create()
    page.setTypeName(className, /*canBeModified = */ false) //must go after dialog.create since create calls page.createControl which calls createTypeNameControls which actually makes the text field

    if (dialog.open() == Window.OK && wizard.getCreatedElement != null) {
      val existingClassPackage = compilationUnit.getPackageDeclarations().head.getElementName //there should always be a package declaration
      val newClassPackage = page.getPackageText()
      if (existingClassPackage != newClassPackage && !page.isDefaultPackage) { //can't import things from the default package, SLS 9.2
        ImportCompletionProposal(page.getFullyQualifiedName).apply(document)
      }
    }
  }
}
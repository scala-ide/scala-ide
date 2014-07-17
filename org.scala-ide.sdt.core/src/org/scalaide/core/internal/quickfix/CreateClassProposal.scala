package org.scalaide.core.internal.quickfix

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.window.Window
import org.eclipse.jface.wizard.WizardDialog
import org.scalaide.core.completion.RelevanceValues
import org.scalaide.ui.internal.ScalaImages
import org.scalaide.ui.internal.wizards.NewFileWizardAdapter
import org.scalaide.util.internal.eclipse.FileUtils

/**
 * Opens a `NewFileWizard`, which has the class creator selected on startup. If
 * the created class is part of a different package than the package of the type
 * where this proposal is invoked on, then the newly created class is automatically
 * imported.
 */
case class CreateClassProposal(className: String, compilationUnit: ICompilationUnit)
  extends BasicCompletionProposal(
    relevance = RelevanceValues.CreateClassProposal,
    displayString = s"Create class '$className'",
    image = ScalaImages.NEW_CLASS.createImage()) {

  override def apply(document: IDocument): Unit = {
    val wizard = new NewFileWizardAdapter("org.scalaide.ui.wizards.classCreator", className)
    wizard.init(JavaPlugin.getDefault().getWorkbench(), new StructuredSelection(compilationUnit))

    val dialog = new WizardDialog(JavaPlugin.getActiveWorkbenchShell(), wizard)
    dialog.create()

    def path = wizard.page.pathOfCreatedFile.flatMap(FileUtils.workspacePath)

    def existsInDifferentPackage: Boolean = {
      path map { newClass =>
        val existingClass = compilationUnit.getPath()
        val pkgPathDiffer = existingClass.segments().toSeq.init != newClass.segments().toSeq.init
        val isNotDefaultPackage = newClass.segmentCount() > 2
        pkgPathDiffer && isNotDefaultPackage
      } getOrElse false
    }

    def importNewlyCreatedClass() = {
      val fullyQualifiedName = path.get.removeFileExtension().segments().drop(2).mkString(".")
      ImportCompletionProposal(fullyQualifiedName).apply(document)
    }

    if (dialog.open() == Window.OK && existsInDifferentPackage)
      importNewlyCreatedClass()
  }

}

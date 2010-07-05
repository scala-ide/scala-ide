/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.refactoring.implementations.OrganizeImports
import scala.tools.refactoring.common.Selections
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.refactoring.ui.LabeledTextField
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IEditorPart
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler

class OrganizeImportsAction extends RefactoringAction {
  
  class OrganizeImportsScalaIdeRefactoring(file: ScalaSourceFile) extends ScalaIdeRefactoring("Organize Imports") {
                  
    val refactoring = file.withCompilerResult(crh => new OrganizeImports {
      val global = crh.compiler
    })
            
    lazy val selection = createSelection(file, 0, 0)
    
    def initialCheck = file.withCompilerResult { crh =>
      refactoring.prepare(selection)
    }
    
    def refactoringParameters = new refactoring.RefactoringParameters    
  }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = Some(new OrganizeImportsScalaIdeRefactoring(file))
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.refactoring.implementations.OrganizeImports
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.common.ConsoleTracing
import scala.tools.refactoring.MultiStageRefactoring
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

class RegenerateAction extends RefactoringAction {
  
  class RegenerateScalaIdeRefactoring(file: ScalaSourceFile) extends ScalaIdeRefactoring("Regenerate Sourcecode") {
    
    abstract class RegenerateRefactoring extends MultiStageRefactoring with ConsoleTracing {
      
      class PreparationResult
      class RefactoringParameters
  
      def prepare(s: Selection): Either[PreparationError, PreparationResult] = Right(new PreparationResult)
    
      def perform(selection: Selection, prepared: PreparationResult, params: RefactoringParameters): Either[RefactoringError, List[Change]] = {
        Right(List(new Change(file.file, 0, file.getSource.length-1, createText(selection.root))))
        
//        Right(refactor(List(selection.root)))
      }
    }
                  
    val refactoring = file.withSourceFile((_,compiler) => new RegenerateRefactoring {
      val global = compiler
    })
            
    lazy val selection = createSelection(file, 0, 0)
    
    def initialCheck = refactoring.prepare(selection)
    
    def refactoringParameters = new refactoring.RefactoringParameters    
  }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = Some(new RegenerateScalaIdeRefactoring(file))
}

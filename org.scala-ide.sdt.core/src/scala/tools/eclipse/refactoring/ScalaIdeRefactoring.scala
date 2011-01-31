/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ltk.core.refactoring.CompositeChange
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import scala.tools.refactoring.MultiStageRefactoring
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.TreeNotFound
import scala.tools.eclipse.util.EclipseResource
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.jface.text.IDocument
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.core.resources.IFile
import org.eclipse.ltk.core.refactoring.Change
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.{Refactoring => LTKRefactoring}
import scala.tools.eclipse.javaelements.ScalaSourceFile

abstract class ScalaIdeRefactoring(val getName: String) extends LTKRefactoring {
  
  def initialCheck: Either[refactoring.PreparationError, refactoring.PreparationResult]
  
  def refactoringParameters: refactoring.RefactoringParameters
  
  val refactoring: MultiStageRefactoring
  
  val selection: refactoring.Selection
  
  def getPages: List[RefactoringWizardPage] = Nil
  
  def createSelection(file: ScalaSourceFile, start: Int, end: Int) = 
	  file.withSourceFile((sourceFile, _) => new refactoring.FileSelection(sourceFile.file, start, end)) ()
    
  var preparationResult: refactoring.PreparationResult = _
  
  private var refactoringError = None: Option[String] 
  
  def createRefactoringChanges() = {
    try {
      refactoring.perform(selection, preparationResult, refactoringParameters) match {
        case Right(result) => 
          Some(result)
        case Left(refactoring.RefactoringError(cause)) => 
          refactoringError = Some(cause)
          None
      }
    } catch {
      case e: TreeNotFound =>
        refactoringError = Some(e.getMessage)
        None
      case e: Exception =>
        refactoringError = Some(e.getMessage)
        None
    }
  }
  
  def createChange(pm: IProgressMonitor): CompositeChange = new CompositeChange(getName) {
       
    createRefactoringChanges() map {
      _ groupBy (_.file) map {
        case (EclipseResource(file: IFile), fileChanges) =>
          new TextFileChange(file.getName(), file) {
            
            val fileChangeRootEdit = new MultiTextEdit
  
            fileChanges map { change =>      
              new ReplaceEdit(change.from, change.to - change.from, change.text)
            } foreach fileChangeRootEdit.addChild
            
            setEdit(fileChangeRootEdit)
          }
      } foreach add
    }
  }
      
  def checkInitialConditions(pm: IProgressMonitor) = new RefactoringStatus {
    initialCheck match {
      case Right(p) => 
        preparationResult = p
      case Left(error) =>
        this.addError(error.cause)
    }
  }
  
  def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
    refactoringError map { error =>
      RefactoringStatus.createErrorStatus(error)
    } getOrElse new RefactoringStatus
  }
}

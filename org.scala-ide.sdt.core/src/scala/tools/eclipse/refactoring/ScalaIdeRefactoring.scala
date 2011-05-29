/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import util.FileUtils
import org.eclipse.core.resources.{IFile, ResourcesPlugin}
import org.eclipse.core.runtime.{IProgressMonitor, CoreException, Status, IStatus, Path}
import org.eclipse.ltk.core.refactoring.{Refactoring => LTKRefactoring, Change, RefactoringStatus, CompositeChange}
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.MultiStageRefactoring
import scala.tools.refactoring.common.{TreeNotFound, Selections}

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
    for (
      changes <- createRefactoringChanges();  
      (abstractFile, fileChanges) <- changes.groupBy(_.file);
      ifile <- FileUtils.toIFile(abstractFile) 
    ) {
     add(EditorHelpers.createTextFileChange(ifile, fileChanges))
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
    refactoringError map RefactoringStatus.createErrorStatus getOrElse new RefactoringStatus
  }
}

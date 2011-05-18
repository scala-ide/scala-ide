/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.{CoreException, IProgressMonitor, IStatus, Status}
import org.eclipse.ltk.core.refactoring.{Change, CompositeChange, Refactoring => LTKRefactoring, RefactoringStatus}
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.ScalaPlugin
import scala.tools.refactoring.common.{InteractiveScalaCompiler, Selections, TreeNotFound}
import scala.tools.refactoring.MultiStageRefactoring

abstract class ScalaIdeRefactoring(val getName: String) extends LTKRefactoring {
  
  def initialCheck: Either[refactoring.PreparationError, refactoring.PreparationResult]
  
  def refactoringParameters: refactoring.RefactoringParameters
  
  val refactoring: MultiStageRefactoring with InteractiveScalaCompiler
  
  val selection: refactoring.Selection
  
  def getPages: List[RefactoringWizardPage] = Nil
  
  def createSelection(file: ScalaSourceFile, start: Int, end: Int) = {
	  file.withSourceFile{(sourceFile, _) =>
	    import refactoring.global
	    
	    val r = new global.Response[global.Tree]
	    global.askLoadedTyped(sourceFile, r)
	    r.get.fold(new refactoring.FileSelection(sourceFile.file, _, start, end), throw _)
	    
    }()
  }
    
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
    createSingleChanges() foreach add
  }
  
  /**
   * Creates the Eclipse change objects from this refactoring instance.
   */
  def createSingleChanges() = {
    createRefactoringChanges().toList flatMap {
      _ groupBy (_.file) map {
        case (abstractFile, fileChanges) =>
          FileUtils.toIFile(abstractFile) map { file =>
            EditorHelpers.createTextFileChange(file, fileChanges)
          } getOrElse {
            val msg = "Could not find the corresponding IFile for "+ abstractFile.path
            throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, 0, msg, null))
          }
      }
    }
  }
      
  def checkInitialConditions(pm: IProgressMonitor) = new RefactoringStatus {
    initialCheck.fold(e => addError(e.cause), preparationResult = _)
  }
  
  def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
    refactoringError map RefactoringStatus.createErrorStatus getOrElse new RefactoringStatus
  }
}

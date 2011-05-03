/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.ltk.ui.refactoring.RefactoringWizard
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.Refactoring
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.action.IAction
import org.eclipse.ui.IEditorActionDelegate
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.PlatformUI
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jface.text.link._

trait RefactoringAction extends ActionAdapter {
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile): Option[ScalaIdeRefactoring]
 
  def createWizard(refactoring: Option[ScalaIdeRefactoring]): RefactoringWizard = refactoring match {
    case Some(refactoring) => new ScalaRefactoringWizard(refactoring)
    case None => new ErrorRefactoringWizard
  }
  
  def createScalaRefactoring(): Option[ScalaIdeRefactoring] = {
    import EditorHelpers._
    
    withScalaFileAndSelection { (scalaFile, selection) =>
      createRefactoring(selection.getOffset, selection.getOffset + selection.getLength, scalaFile)
    }
  }
  
  def runRefactoring(wizard: RefactoringWizard) {
    
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell
          
    new RefactoringWizardOpenOperation(wizard) run (shell, "EM")
  }
  
  def run(action: IAction) {
    
    val refactoring = createScalaRefactoring()
    
    val wizard = createWizard(refactoring)
    
    runRefactoring(wizard)
  }
    
  def runInLinkedModeUi(ps: List[(Int, Int)]) = {
    
    for (editor <- EditorHelpers.currentEditor) {
        
      val model = new LinkedModeModel {
        
        this addGroup new LinkedPositionGroup {
        
          val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
          
          ps foreach (p => addPosition(new LinkedPosition(document, p._1, p._2)))
        }
      
        forceInstall
      }

      (new LinkedModeUI(model, editor.sourceViewer)).enter
    }
  }
}

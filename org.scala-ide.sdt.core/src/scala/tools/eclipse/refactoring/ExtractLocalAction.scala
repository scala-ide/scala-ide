/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.jface.action.IAction
import javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.{Change, Selections}
import scala.tools.refactoring.implementations.ExtractLocal
import org.eclipse.ui.PlatformUI
import scala.tools.refactoring.common.TextChange

/**
 * From a selected expression, the Extract Local refactoring will create a new 
 * value in the closest enclosing scope and replace the selected expression with 
 * a reference to that value. 
 * 
 * Extract Local also uses Eclipse's linked UI mode.
 */
class ExtractLocalAction extends RefactoringAction {
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractLocalScalaIdeRefactoring(selectionStart, selectionEnd, file)
  
  class ExtractLocalScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Local", file, start, end) {
              
    val refactoring = withCompiler( c => new ExtractLocal { val global = c })
    
    val name = "extractedLocalValue"
                    
    def refactoringParameters = name
  }
       
  override def run(action: IAction) {
    
    /**
     * Inline extracting is implemented by extracting to a new name
     * that does not exist and then looking up the position of these
     * names in the generated change.
     */
    def doInlineExtraction(change: TextChange, name: String) {
      EditorHelpers.doWithCurrentEditor { editor =>
                
        EditorHelpers.applyRefactoringChangeToEditor(change, editor)
        
        val occurrences = {
          val firstOccurrence  = change.text.indexOf(name)
          val secondOccurrence = change.text.indexOf(name, firstOccurrence + 1)                  
          List(firstOccurrence, secondOccurrence) map (o => (change.from + o, name.length))
        }
        
        EditorHelpers.enterLinkedModeUi(occurrences)
      }      
    }
    
    val shell = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getShell
    
    createScalaIdeRefactoringForCurrentEditorAndSelection() match {
      case Some(r: ExtractLocalScalaIdeRefactoring) => 
              
        r.preparationResult.right.map(_ => r.performRefactoring()) match {
          case Right((change: TextChange) :: Nil) =>
            doInlineExtraction(change, r.name)
          case _ =>
            runRefactoring(createWizardForRefactoring(Some(r)), shell)
        }
        
      case None => runRefactoring(createWizardForRefactoring(None), shell)
    }
  }
}

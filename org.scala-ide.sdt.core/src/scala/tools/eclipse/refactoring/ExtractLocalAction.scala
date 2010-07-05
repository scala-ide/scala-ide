/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.analysis.GlobalIndexes
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.refactoring.ui._
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import scala.tools.refactoring.implementations.ExtractLocal
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.IEditorPart
import org.eclipse.jface.action.IAction
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.IEditorActionDelegate
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.ReplaceEdit
import scala.tools.eclipse.util.EclipseResource

class ExtractLocalAction extends RefactoringAction {
  
  class ExtractLocalScalaIdeRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Local") {
    outer =>
          
    val name = "$_unused_name"
    
    val refactoring = file.withCompilerResult(crh => new ExtractLocal {
      val global = crh.compiler
    })
    
    lazy val selection = createSelection(file, selectionStart, selectionEnd)
            
    def initialCheck = file.withCompilerResult { crh =>
      refactoring.prepare(selection)
    }
    
    def refactoringParameters = new refactoring.RefactoringParameters {
      val name = outer.name
    }
  }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = Some(new ExtractLocalScalaIdeRefactoring(selectionStart, selectionEnd, file))
      
  override def run(action: IAction) {
    
    createScalaRefactoring() match {
      case Some(r: ExtractLocalScalaIdeRefactoring) => 
      
        r.checkInitialConditions(new NullProgressMonitor)
        
        r.preparationResult match {
          case _: r.refactoring.PreparationResult => 
          
            r.createRefactoringChanges() match {
              case Some(Change(_, from, to, text) :: Nil) =>
              
              	// inline extracting is implemented by extracting to a new name
                // that does not exist and then looking up the position of these
                // names in the generated change.
              
                val newName = "x"
                val firstOccurence = text.indexOf(r.name)
                val secondOccurrence = text.indexOf(r.name, firstOccurence + 1) + newName.length - r.name.length
                
                val edit = new ReplaceEdit(from, to - from, text.replace(r.name, newName))
                
                EditorHelpers.withCurrentEditor { editor =>
                
                  val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
                  
                  edit.apply(document)
                  
                  runInLinkedModeUi((from + firstOccurence, newName.length) :: (from + secondOccurrence, newName.length) :: Nil)
                  
                  None
                }
                
              case _ => runRefactoring(createWizard(Some(r)))
            }
          case _ => runRefactoring(createWizard(Some(r)))
        }
      case None => runRefactoring(createWizard(None))
    }
  }
}

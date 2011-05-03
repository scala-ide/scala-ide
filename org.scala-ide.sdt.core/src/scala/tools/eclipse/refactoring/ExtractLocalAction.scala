/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.{NullProgressMonitor, IProgressMonitor}
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ltk.core.refactoring.{TextFileChange, RefactoringStatus}
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.swt.events.{ModifyListener, ModifyEvent}
import org.eclipse.text.edits.{ReplaceEdit, MultiTextEdit}
import org.eclipse.ui.{IEditorActionDelegate, IWorkbenchWindowActionDelegate, IEditorPart, IWorkbenchWindow, IFileEditorInput}
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui._
import scala.tools.eclipse.util.EclipseResource
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.{Change, Selections}
import scala.tools.refactoring.implementations.ExtractLocal

class ExtractLocalAction extends RefactoringAction {
  
  class ExtractLocalScalaIdeRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Local") {
    outer =>
          
    val name = "$_unused_name"
    
    val refactoring = file.withSourceFile((_, compiler) => new ExtractLocal {
      val global = compiler
    })()
    
    lazy val selection = createSelection(file, selectionStart, selectionEnd)
            
    def initialCheck = refactoring.prepare(selection)
    
    def refactoringParameters = outer.name
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
                
                for (editor <- EditorHelpers.currentEditor) {
                
                  val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
                  
                  edit.apply(document)
                  
                  runInLinkedModeUi((from + firstOccurence, newName.length) :: (from + secondOccurrence, newName.length) :: Nil)
                }
                
              case _ => runRefactoring(createWizard(Some(r)))
            }
          case _ => runRefactoring(createWizard(Some(r)))
        }
      case None => runRefactoring(createWizard(None))
    }
  }
}

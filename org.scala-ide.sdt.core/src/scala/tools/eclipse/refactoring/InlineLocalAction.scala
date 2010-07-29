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
import scala.tools.refactoring.implementations.InlineLocal
import org.eclipse.ltk.ui.refactoring.RefactoringWizard

class InlineLocalAction extends RefactoringAction {
  
  class InlineLocalScalaIdeRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Inline Local") {
          
    val refactoring = file.withCompilerResult(crh => new InlineLocal with GlobalIndexes {
      val global = crh.compiler
      val index = GlobalIndex(global.unitOfFile(crh.sourceFile.file).body)
    })
    
    lazy val selection = createSelection(file, selectionStart, selectionEnd)
            
    def initialCheck = refactoring.prepare(selection)
    
    def refactoringParameters = new refactoring.RefactoringParameters
  }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = 
    Some(new InlineLocalScalaIdeRefactoring(selectionStart, selectionEnd, file))
  
  override def run(action: IAction) {
    // if there are no errors, we want to apply the refactoring without showing the change preview
    createScalaRefactoring() match {
      case Some(refactoring: InlineLocalScalaIdeRefactoring) =>
        val npm = new NullProgressMonitor
        val status = refactoring.checkInitialConditions(npm)
        
        if(status.hasError) {
          runRefactoring(createWizard(Some(refactoring)))
          return
        }
        
        val change = refactoring.createChange(npm)
        change.perform(npm)
      case _ => //?
    }
  }
}

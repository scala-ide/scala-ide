/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.action.IAction
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.ui.refactoring.{RefactoringWizardOpenOperation, UserInputWizardPage}
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ModifyListener, ModifyEvent}
import org.eclipse.swt.layout.{GridLayout, GridData}
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.{IEditorActionDelegate, IWorkbenchWindowActionDelegate, IEditorPart, IWorkbenchWindow, IFileEditorInput}
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui._
import scala.tools.refactoring.Refactoring
import scala.tools.refactoring.analysis.{NameValidation, GlobalIndexes}
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.implementations.ExtractMethod

class ExtractMethodAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = 
    Some(new ExtractMethodScalaIdeRefactoring(selectionStart, selectionEnd, file))

  class ExtractMethodScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Method") {
   
    var name = ""
    
    val refactoring = file.withCompilerResult(crh => new ExtractMethod with GlobalIndexes with NameValidation {
      val global = crh.compiler
      val index = GlobalIndex(global.unitOfFile(crh.sourceFile.file).body)
    })

    lazy val selection = createSelection(file, start, end)
            
    def initialCheck = file.withCompilerResult { crh =>
      refactoring.prepare(selection)
    }
    
    def refactoringParameters = name
    
    override def getPages = new NewNameWizardPage(s => name = s, refactoring.isValidIdentifier, "extractedMethod") :: Nil
    
  }
}

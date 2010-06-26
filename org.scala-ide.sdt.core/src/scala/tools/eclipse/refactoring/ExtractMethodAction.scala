/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.analysis.NameValidation
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.refactoring.ui._
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.jface.text.ITextSelection
import scala.tools.refactoring.implementations.ExtractMethod
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
import scala.tools.refactoring.Refactoring

class ExtractMethodAction extends RefactoringAction {
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = Some(new ScalaIdeRefactoring("Extract Method") {
            
    var name = ""
    
    val refactoring = file.withCompilerResult(crh => new ExtractMethod with GlobalIndexes with NameValidation {
      val global = crh.compiler
      val index = GlobalIndex(global.unitOfFile(crh.sourceFile.file).body)
    })

    lazy val selection = createSelection(file, selectionStart, selectionEnd)
            
    def initialCheck = file.withCompilerResult { crh =>
      refactoring.prepare(selection)
    }
    
    def refactoringParameters = new refactoring.RefactoringParameters {
      val methodName = name
    }
    
    override def getPages = new NewNameWizardPage((s => name = s), refactoring.isValidIdentifier, "extractedMethod") :: Nil
  })
}

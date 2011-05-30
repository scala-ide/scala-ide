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

/**
 * Extracts a series of statements into a new method, passing the needed 
 * parameters and return values.
 * 
 * The implementation found for example in the JDT offers much more configuration
 * options, for now, we only require the user to provide a name.
 */
class ExtractMethodAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractMethodScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExtractMethodScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Method", file, start, end) {
    
    val refactoring = file.withSourceFile((sourceFile, compiler) => new ExtractMethod with GlobalIndexes with NameValidation {
      val global = compiler
      val index = GlobalIndex(askLoadedAndTypedTreeForFile(sourceFile).left.get)
    })()
    
    var name = ""

    def refactoringParameters = name
    
    override def getPages = new NewNameWizardPage(s => name = s, refactoring.isValidIdentifier, "extractedMethod", "refactoring_extract_method") :: Nil
    
  }
}

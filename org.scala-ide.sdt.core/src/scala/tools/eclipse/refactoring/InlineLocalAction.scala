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

/**
 * The Inline Local -- also known as Inline Temp -- refactoring is the dual to Extract Local.
 * It can be used to eliminate a local values by replacing all references to the local value
 * by its right hand side.
 *
 * The implementation does not show a wizard but directly applies the changes (ActionWithNoWizard trait).
 */
class InlineLocalAction extends RefactoringAction with ActionWithNoWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new InlineLocalScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class InlineLocalScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Inline Local", file, start, end) {

    val refactoring = file.withSourceFile((sourceFile, compiler) => new InlineLocal with GlobalIndexes {
      val global = compiler
      val index = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        global.ask(() => GlobalIndex(tree))
      }
    })()

    /**
     * The refactoring does not take any parameters.
     */
    def refactoringParameters = new refactoring.RefactoringParameters
  }
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.eclipse.Tracer
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.analysis.Indexes
import scala.tools.refactoring.common._
import scala.tools.refactoring.Refactoring
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.refactoring.ui._
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation
import org.eclipse.jface.text.ITextSelection
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
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.text.link._

class ShowReferencesAction extends RefactoringAction {
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = {

    file.withCompilerResult{ crh => 
    
      val refactoring = new GlobalIndexes with Selections with PimpedTrees { 
        val global = crh.compiler
        val index: IndexLookup = null
      }
              
      val (from, to) = (selectionStart, selectionEnd) match {
        case (f, t) if f == t => (f - 1, t + 1) // pretend that we have a selection
        case x => x
      }
      
      val selection = new refactoring.FileSelection(crh.sourceFile.file, from, to)
                          
      EditorHelpers.withCurrentEditor { editor =>
      
        val compilationUnitIndices = ScalaPlugin.plugin.getScalaProject(editor.getEditorInput).allSourceFiles.toList flatMap { f =>
          
          ScalaSourceFile.createFromPath(f.getFullPath.toString) map (_.withCompilerResult { crh => 
            if(crh.body.pos.isRange) {
              Some(refactoring.CompilationUnitIndex(refactoring.global.unitOf(crh.body.pos.source).body))
            } else {
              Tracer.println("skipped indexing "+ f.getFullPath.toString)
              None
            }
          })
        } flatten
        
        val index = refactoring.GlobalIndex(compilationUnitIndices)
        
        selection.selectedSymbolTree map { symTree =>
            Tracer.println(symTree.symbol.nameString +" is referenced in: ")
            index.references(symTree.symbol) foreach { t =>
            Tracer.println("%s:%s\t%s".format(t.pos.source.file.name, t.pos.line, t.pos.lineContent))
          }
        }

        None
      }
    }
  }
}

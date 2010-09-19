/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.eclipse.Tracer
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.action.IAction
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.ui.refactoring.{RefactoringWizardOpenOperation, UserInputWizardPage}
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui._
import scala.tools.refactoring.Refactoring
import scala.tools.refactoring.analysis.{NameValidation, GlobalIndexes}
import scala.tools.refactoring.common.ConsoleTracing

import scala.tools.refactoring.implementations.Rename

class RenameAction extends RefactoringAction {
  
  class RenameScalaIdeRefactoring(selectedFrom: Int, selectedTo: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Rename") {
      
      var name = ""
            
      val refactoring = file.withCompilerResult(crh => new Rename with ConsoleTracing with GlobalIndexes with NameValidation { 
        val global = crh.compiler
        var index = GlobalIndex(Nil)
      })
      
      import refactoring._
              
      lazy val selection = createSelection(file, selectedFrom, selectedTo)
      
      lazy val initialCheck = file.withCompilerResult { crh =>
        prepare(selection) match {
          
          case r @ Right(PreparationResult(selectedLocal, true)) =>
          
            name = selectedLocal.symbol.nameString
            
            if(crh.body.pos == global.NoPosition)
              Left(PreparationError("Could not get AST for current compilation unit."))
            else {
              index = GlobalIndex(global.unitOf(crh.body.pos.source).body)
              r
            }
            
          case r @ Right(PreparationResult(selectedLocal, false)) =>
          
            name = selectedLocal.symbol.nameString

            val allProjectSourceFiles = (EditorHelpers.withCurrentEditor { editor =>
              Some(ScalaPlugin.plugin.getScalaProject(editor.getEditorInput).allSourceFiles.toList)
            }) getOrElse Nil
            
            // TODO index in the background while the user inputs the name
            val cus = allProjectSourceFiles flatMap { f =>
            
              ScalaSourceFile.createFromPath(f.getFullPath.toString) map (_.withCompilerResult { crh => 
                if(crh.body.pos.isRange) {
                  Tracer.println("indexing "+ crh.body.pos.source.file.name)
                  List(CompilationUnitIndex(global.unitOf(crh.body.pos.source).body))
                } else {
                  Tracer.println("skipped indexing "+ f.getFullPath.toString)
                  Nil
                }
              })
            } flatten
            
            index = GlobalIndex(cus)
            r
          case l @ Left(_) => l
        }
      }
      
      override def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
        val status = super.checkFinalConditions(pm)
        
        refactoring.doesNameCollide(name, selection.selectedSymbolTree map (_.symbol) getOrElse refactoring.global.NoSymbol) match {
          case Nil => ()
          case collisions => 
            val names = collisions map (s => s.fullName) mkString ", "
            status.addWarning("The name \""+ name +"\" is already in use: "+ names)
        }
        
        
        status
      }
      
      def refactoringParameters = name
      
      override def getPages = new NewNameWizardPage((s => name = s), refactoring.isValidIdentifier, name, "refactoring_rename") :: Nil
    }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = {
        
    val (from, to) = (selectionStart, selectionEnd) match {
      case (f, t) if f == t => (f - 1, t + 1) // pretend that we have a selection
      case x => x
    }
    
    Some(new RenameScalaIdeRefactoring(from, to, file))
  }
  
  override def run(action: IAction) {

    def runInlineRename(r: RenameScalaIdeRefactoring) {
      import r.refactoring._
      
      val positions = index.occurences(r.selection.selectedSymbolTree.get.symbol) map (_.namePosition) map (pos => (pos.start, pos.end - pos.start))
      
      runInLinkedModeUi(positions)
    }
    
    createScalaRefactoring() match {
      case Some(r: RenameScalaIdeRefactoring) => 
        r.initialCheck match {
          case Right(r.refactoring.PreparationResult(_, true)) => runInlineRename(r)
          case _ => runRefactoring(createWizard(Some(r)))
        }
      case None => runRefactoring(createWizard(None))
    }
  }
}

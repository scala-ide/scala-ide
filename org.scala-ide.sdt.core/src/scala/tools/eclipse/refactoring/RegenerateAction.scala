/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.common.ConsoleTracing
import scala.tools.refactoring.MultiStageRefactoring
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.ScalaPresentationCompiler

class RegenerateAction extends RefactoringAction {
  
  class RegenerateScalaIdeRefactoring(file: ScalaSourceFile) extends ScalaIdeRefactoring("Regenerate Sourcecode") {
    
    abstract class RegenerateRefactoring extends MultiStageRefactoring with ConsoleTracing {
      
      class PreparationResult
      class RefactoringParameters
  
      def prepare(s: Selection): Either[PreparationError, PreparationResult] = Right(new PreparationResult)
    
      def perform(selection: Selection, prepared: PreparationResult, params: RefactoringParameters): Either[RefactoringError, List[Change]] = {
        Right(List(new Change(file.file, 0, file.getSource.length-1, createText(selection.root))))
        
//        Right(refactor(List(selection.root)))
      }
    }
                  
    val refactoring = file.withSourceFile((_,compiler) => new RegenerateRefactoring {
      val global = compiler
    }) ()
            
    lazy val selection = createSelection(file, 0, 0)
    
    def initialCheck = refactoring.prepare(selection)
    
    def refactoringParameters = new refactoring.RefactoringParameters    
  }
  
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = Some(new RegenerateScalaIdeRefactoring(file))
}

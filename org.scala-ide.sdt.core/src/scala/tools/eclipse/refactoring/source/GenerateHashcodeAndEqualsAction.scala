package scala.tools.eclipse
package refactoring.source

import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.GenerateHashcodeAndEquals

import javaelements.ScalaSourceFile
import ui.GenerateHashcodeAndEqualsConfigurationPage

/**
 * This refactoring that generates hashCode and equals implementations 
 * following the recommendations given in chapter 28 of
 * Programming in Scala.
 */
class GenerateHashcodeAndEqualsAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new GenerateHashcodeAndEqualsScalaIdeRefactoring(selectionStart, selectionEnd, file)
  
  class GenerateHashcodeAndEqualsScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) 
    extends ClassParameterDrivenIdeRefactoring("Generate hashCode and equals", start, end, file) {
    
    val refactoring = withCompiler { c => 
      new GenerateHashcodeAndEquals {
        val global = c 
      }
    }
    
    val configPage = new GenerateHashcodeAndEqualsConfigurationPage(
        classParams.map(_.name.toString), 
        selectedClassParamNames_=,
        callSuper_=)
  }
}
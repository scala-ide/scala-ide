package scala.tools.eclipse
package refactoring.source

import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.IntroduceProductNTrait

import javaelements.ScalaSourceFile
import ui.IntroduceProductNTraitConfigurationPage
/**
 * This refactoring implements the ProductN trait for a class.
 * Given N selected class parameters this refactoring generates
 * the methods needed to implement the ProductN trait. This includes
 * implementations for hashCode and equals.
 * @see GenerateHashcodeAndEqualsAction
 */
class IntroduceProductNTraitAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new GenerateHashcodeAndEqualsScalaIdeRefactoring(selectionStart, selectionEnd, file)
  
  class GenerateHashcodeAndEqualsScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) 
    extends ClassParameterDrivenIdeRefactoring("Generate hashCode and equals", start, end, file) {
    
    val refactoring = withCompiler { c => 
      new IntroduceProductNTrait{
        val global = c 
      }
    }
    
    val configPage = new IntroduceProductNTraitConfigurationPage(
        classParams.map(_.name.toString),
        selectedNames => selectedClassParamNames = selectedNames,
        callSuperNew => callSuper = callSuperNew)
  }
}
package scala.tools.eclipse
package refactoring.source

import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.GenerateHashcodeAndEquals
import javaelements.ScalaSourceFile
import ui.GenerateHashcodeAndEqualsConfigurationPageGenerator
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage

/**
 * This refactoring that generates hashCode and equals implementations
 * following the recommendations given in chapter 28 of
 * Programming in Scala.
 */
class GenerateHashcodeAndEqualsAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new GenerateHashcodeAndEqualsScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class GenerateHashcodeAndEqualsScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ClassParameterDrivenIdeRefactoring("Generate hashCode and equals", start, end, file) with GenerateHashcodeAndEqualsConfigurationPageGenerator {

    val refactoring = withCompiler { c =>
      new GenerateHashcodeAndEquals {
        val global = c
      }
    }

    import refactoring.global.ValDef

    override private[source] def configPage(prepResult: refactoring.PreparationResult): RefactoringWizardPage =
      new GenerateHashcodeAndEqualsConfigurationPage(
        prepResult,
        selectedClassParamNames_=,
        callSuper_=,
        keepExistingEqualityMethods_=)

  }
}
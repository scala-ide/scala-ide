package org.scalaide.refactoring.internal
package source

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.implementations.GenerateHashcodeAndEquals
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import ui.GenerateHashcodeAndEqualsConfigurationPageGenerator

/**
 * This refactoring that generates hashCode and equals implementations
 * following the recommendations given in chapter 28 of
 * Programming in Scala.
 */
class GenerateHashcodeAndEqualsAction extends RefactoringActionWithWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new GenerateHashcodeAndEqualsScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class GenerateHashcodeAndEqualsScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ClassParameterDrivenIdeRefactoring("Generate hashCode and equals", start, end, file) with GenerateHashcodeAndEqualsConfigurationPageGenerator {

    val refactoring = withCompiler { c =>
      new GenerateHashcodeAndEquals {
        val global = c
      }
    }

    override private[source] def configPage(prepResult: refactoring.PreparationResult): RefactoringWizardPage =
      new GenerateHashcodeAndEqualsConfigurationPage(
        prepResult,
        selectedClassParamNames_=,
        callSuper_=,
        keepExistingEqualityMethods_=)

  }
}

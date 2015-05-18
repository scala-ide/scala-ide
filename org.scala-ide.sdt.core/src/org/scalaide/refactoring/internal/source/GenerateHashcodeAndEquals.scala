package org.scalaide.refactoring.internal
package source

import scala.tools.refactoring.implementations

import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.GenerateHashcodeAndEquals
import org.scalaide.refactoring.internal.RefactoringExecutorWithWizard

import ui.GenerateHashcodeAndEqualsConfigurationPageGenerator

/**
 * This refactoring generates hashCode and equals implementations by
 * following the recommendations given in chapter 28 of
 * Programming in Scala.
 */
class GenerateHashcodeAndEquals extends RefactoringExecutorWithWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new GenerateHashcodeAndEqualsScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class GenerateHashcodeAndEqualsScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ClassParameterDrivenIdeRefactoring(GenerateHashcodeAndEquals, "Generate hashCode and equals", start, end, file) with GenerateHashcodeAndEqualsConfigurationPageGenerator {

    val refactoring = withCompiler { c =>
      new implementations.GenerateHashcodeAndEquals {
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

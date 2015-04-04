package org.scalaide.refactoring.internal
package source

import scala.tools.refactoring.implementations

import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.IntroduceProductNTrait
import org.scalaide.refactoring.internal.RefactoringExecutorWithWizard

import ui.IntroduceProductNTraitConfigurationPageGenerator

/**
 * This refactoring implements the ProductN trait for a class.
 * Given N selected class parameters this refactoring generates
 * the methods needed to implement the ProductN trait. This includes
 * implementations for hashCode and equals.
 * @see GenerateHashcodeAndEquals
 */
class IntroduceProductNTrait extends RefactoringExecutorWithWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new IntroduceProductNTraitRefactoring(selectionStart, selectionEnd, file)

  class IntroduceProductNTraitRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ClassParameterDrivenIdeRefactoring(IntroduceProductNTrait, "Introduce ProductN trait", start, end, file) with IntroduceProductNTraitConfigurationPageGenerator {

    val refactoring = withCompiler { c =>
      new implementations.IntroduceProductNTrait {
        val global = c
      }
    }

    override private[source] def configPage(prepResult: refactoring.PreparationResult): RefactoringWizardPage =
      new IntroduceProductNTraitConfigurationPage(
        prepResult,
        selectedClassParamNames_=,
        callSuper_=,
        keepExistingEqualityMethods_=)

  }
}

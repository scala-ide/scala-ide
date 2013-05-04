package scala.tools.eclipse.refactoring.source
package ui

trait IntroduceProductNTraitConfigurationPageGenerator extends GenerateHashcodeAndEqualsConfigurationPageGenerator {

  this: ClassParameterDrivenIdeRefactoring =>

  import refactoring._

  /**
   * Wizard page for the IntroduceProductNTrait refactoring.
   */
  class IntroduceProductNTraitConfigurationPage(
      prepResult: PreparationResult,
      selectedParamsObs: List[String] => Unit,
      callSuperObs: Boolean => Unit,
      keepExistingEqualityMethodsObs: Boolean => Unit)
      extends GenerateHashcodeAndEqualsConfigurationPage(
          prepResult,
          selectedParamsObs,
          callSuperObs,
          keepExistingEqualityMethodsObs) {

    override val headerLabelText = "Select the class parameters for the ProductN trait"

  }
}
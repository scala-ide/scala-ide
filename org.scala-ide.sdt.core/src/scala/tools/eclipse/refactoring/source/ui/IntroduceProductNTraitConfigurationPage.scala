package scala.tools.eclipse.refactoring.source.ui

/**
 * Wizard page for the IntroduceProductNTrait refactoring.
 */
class IntroduceProductNTraitConfigurationPage(
    classParamNames: List[String], 
    selectedParamsObs: List[String] => Unit,
    callSuperObs: Boolean => Unit) 
    extends GenerateHashcodeAndEqualsConfigurationPage(classParamNames, selectedParamsObs, callSuperObs) {

  override val headerLabelText = "Select the class parameters for the ProductN trait"
  
}
package scala.tools.eclipse.refactoring.method.ui

import org.eclipse.swt.widgets.Button
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.refactoring.Refactoring

/**
 * Generates the wizard page for a MergeParameterLists refactoring.
 */
trait MergeParameterListsConfigurationPageGenerator extends MethodSignatureRefactoringConfigurationPageGenerator {

  this: ScalaIdeRefactoring =>

  import refactoring.global._

  override type MSRefactoringParameters = List[Int]

  override val refactoringCaption = "Merge parameter lists"

  override def mkConfigPage(method: DefDef, paramsObs: MSRefactoringParameters => Unit) = new MergeParameterListsConfigurationPage(method, paramsObs)

  class MergeParameterListsConfigurationPage(
      method: DefDef,
      paramsObs: MSRefactoringParameters => Unit) extends MethodSignatureRefactoringConfigurationPage(method, paramsObs) {

    override val headerLabelText = "Merge parameter lists"

    // We need to remember the original parameter lists
    private val paramsWithOriginalSeparators = intersperse(method.vparamss, nr => OriginalSeparator(nr))

    // If a parameter is selected we disable the merge button and enable
    // the split button if the selected parameter was originally before
    // a separator to make it possible to revert a previously triggered merge.
    override def setBtnStatesForParameter(
        param: ValDef,
        paramsWithSeparators: List[ParamOrSeparator],
        splitBtn: Button,
        mergeBtn: Button) {
      mergeBtn.setEnabled(false)
      val isBeforeSeparatorCurrently = isBeforeSeparator(param, paramsWithSeparators)
      val isBeforeSeparatorOriginally = isBeforeSeparator(param, paramsWithOriginalSeparators)
      splitBtn.setEnabled(!isBeforeSeparatorCurrently && isBeforeSeparatorOriginally)
    }

    // If a separator is selected we enable the merge button and disable the split button.
    override def setBtnStatesForSeparator(
        separator: ParamListSeparator,
        paramsWithSeparators: List[ParamOrSeparator],
        splitBtn: Button,
        mergeBtn: Button) {
      mergeBtn.setEnabled(true)
      splitBtn.setEnabled(false)
    }

    override def computeParameters(paramsWithSeparators: List[ParamOrSeparator]) = {
      val currentSeparatorPositions = paramsWithSeparators.collect{case Right(OriginalSeparator(nr)) => nr}
      val originalSeparatorPositions = paramsWithOriginalSeparators.collect{case Right(OriginalSeparator(nr)) => nr}
      originalSeparatorPositions diff currentSeparatorPositions
    }

    // Handles a click of the split button; reinserts an original separator.
    override def handleFirstBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Left(param) => {
        val originalFollowingSeparator = followingSeparator(param, paramsWithOriginalSeparators)
        originalFollowingSeparator.map(insertSeparatorAfter(param, _, paramsWithSeparators)).getOrElse(paramsWithSeparators)
      }
      case _ => paramsWithSeparators
    }

    // Handles a click of the merge button; removes the selected separator.
    override def handleSecondBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Right(sep @ OriginalSeparator(_)) => removeSeparator(sep, paramsWithSeparators)
      case _ => paramsWithSeparators
    }

  }
}
package scala.tools.eclipse
package refactoring.method.ui

import org.eclipse.swt.widgets.Button
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.refactoring.Refactoring

/**
 * Generates the wizard page for a SplitParameterLists refactoring.
 */
trait SplitParameterListsConfigurationPageGenerator extends MethodSignatureRefactoringConfigurationPageGenerator {

  this: ScalaIdeRefactoring =>

  import refactoring.global._

  override type MSRefactoringParameters = List[List[Int]]

  override val refactoringCaption = "Split parameter lists"

  override def mkConfigPage(method: DefDef, paramsObs: MSRefactoringParameters => Unit) = new SplitParameterListsConfigurationPage(method, paramsObs)

  class SplitParameterListsConfigurationPage(
    method: DefDef,
    paramsObs: MSRefactoringParameters => Unit) extends MethodSignatureRefactoringConfigurationPage(method, paramsObs) {

    override val headerLabelText = "Split parameter lists after the marked parameter"

    // If a parameter is selected in the parameter table we disable the
    // merge button and enable the split button if the selected parameter
    // is in a splittable position (not the last parameter in its list).
    override def setBtnStatesForParameter(
        param: ValDef,
        paramsWithSeparators: List[ParamOrSeparator],
        splitBtn: Button,
        mergeBtn: Button) {
      mergeBtn.setEnabled(false)
      splitBtn.setEnabled(isInSplitPosition(param, paramsWithSeparators))
    }

    // If a separator is selected we disable the split button and
    // enable the merge button if the separator was previously inserted
    // to make it possible to revert this decision.
    override def setBtnStatesForSeparator(
      separator: ParamListSeparator,
      paramsWithSeparators: List[ParamOrSeparator],
      splitBtn: Button,
      mergeBtn: Button) {
      separator match {
        case OriginalSeparator(_) => mergeBtn.setEnabled(false)
        case InsertedSeparator(_, _) => mergeBtn.setEnabled(true)
      }
      splitBtn.setEnabled(false)
    }

    override def computeParameters(paramsWithSeparators: List[ParamOrSeparator]) = {
      val splitters = paramsWithSeparators collect { case Right(sep @ InsertedSeparator(_, _)) => sep }
      val grouped = splitters.groupBy(sep => sep.paramListIndex).withDefaultValue(Nil)
      val splitterLists =
        for (i <- 0 until method.vparamss.size)
          yield grouped(i)
      val splitPositions = splitterLists.map(splitters => splitters.map(_.splitPosition).sortWith(_ < _)).toList
      splitPositions
    }

    // Handles a click of the split button; inserts a separator after the selected parameter.
    override def handleFirstBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Left(param) if isInSplitPosition(param, paramsWithSeparators) => addSplitPositionAfter(param, paramsWithSeparators)
      case _ => paramsWithSeparators
    }

    // Handles a click of the merge button; removes the selected inserted separator.
    override def handleSecondBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Right(sep: InsertedSeparator) => removeSeparator(sep, paramsWithSeparators)
      case _ => paramsWithSeparators
    }

  }
}
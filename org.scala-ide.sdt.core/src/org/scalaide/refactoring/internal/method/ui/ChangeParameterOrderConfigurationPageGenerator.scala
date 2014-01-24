package scala.tools.eclipse.refactoring.method.ui

import org.eclipse.swt.widgets.Button
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.refactoring.Refactoring


/**
 * Generates the wizard page for a ChangeParameterOrder refactoring.
 */
trait ChangeParameterOrderConfigurationPageGenerator extends MethodSignatureRefactoringConfigurationPageGenerator {

  this: ScalaIdeRefactoring =>

  import refactoring.global._

  override type MSRefactoringParameters = List[List[Int]]

  override val refactoringCaption = "Change parameter order"

  override def mkConfigPage(method: DefDef, paramsObs: MSRefactoringParameters => Unit) = new ChangeParameterOrderConfigurationPage(method, paramsObs)

  class ChangeParameterOrderConfigurationPage(
      method: DefDef,
      paramsObs: MSRefactoringParameters => Unit) extends MethodSignatureRefactoringConfigurationPage(method, paramsObs) {

    override val headerLabelText = "Change the order of parameters inside a parameter list"
    // We use the first button to move a parameter up...
    override val firstBtnText = "Up"
    // ...and the second button to move it down.
    override val secondBtnText = "Down"

    // If a parameter is selected we
    // - enable the up button when the selected parameter is not
    //   the first one in its parameter list and disable it otherwise
    // - enable the down button when the selected parameter is not
    //   the last one in its parameter list and disable it otherwise
    override def setBtnStatesForParameter(
        param: ValDef,
        paramsWithSeparators: List[ParamOrSeparator],
        upBtn: Button,
        downBtn: Button) {
      val isLast = isLastInParamList(param, paramsWithSeparators)
      val isFirst = isFirstInParamList(param, paramsWithSeparators)

      downBtn.setEnabled(!isLast)
      upBtn.setEnabled(!isFirst)
    }

    // If a parameter is selected we disable both buttons.
    override def setBtnStatesForSeparator(
        separator: ParamListSeparator,
        paramsWithSeparators: List[ParamOrSeparator],
        upBtn: Button,
        downBtn: Button) {
      downBtn.setEnabled(false)
      upBtn.setEnabled(false)
    }

    override def computeParameters(paramsWithSeparators: List[ParamOrSeparator]) = {
      def computePermutation(paramLists: (List[ValDef], List[ValDef])) = {
        val original = paramLists._1
        val permuted = paramLists._2
        permuted.map(p => original.indexOf(p))
      }

      val permutedParamLists = extractParamLists(paramsWithSeparators)
      val originalParamLists = method.vparamss
      val permutations = originalParamLists.zip(permutedParamLists).map(computePermutation)
      println(permutations)
      permutations
    }

    // Handles a click on the up button; moves the selected parameter up
    override def handleFirstBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Left(param) => moveParamUp(param, paramsWithSeparators)
      case _ => paramsWithSeparators
    }

    private def moveParamUp(
        param: ValDef,
        paramsWithSeparators: List[ParamOrSeparator]):
          List[ParamOrSeparator] = paramsWithSeparators match {
      case Nil => Nil
      case p::Nil => paramsWithSeparators
      case Left(first)::Left(second)::rest if second == param => Left(second)::Left(first)::rest
      case p::ps => p::moveParamUp(param, ps)
    }

    // Handles a click on the down button; moves the selected parameter down
    override def handleSecondBtn(selection: ParamOrSeparator, paramsWithSeparators: List[ParamOrSeparator]) = selection match {
      case Left(param) => moveParamDown(param, paramsWithSeparators)
      case _ => paramsWithSeparators
    }

    private def moveParamDown(
        param: ValDef,
        paramsWithSeparators: List[ParamOrSeparator]):
          List[ParamOrSeparator] = paramsWithSeparators match {
      case Nil => Nil
      case p::Nil => paramsWithSeparators
      case Left(first)::Left(second)::rest if first == param => Left(second)::Left(first)::rest
      case p::ps => p::moveParamDown(param, ps)
    }

  }

}
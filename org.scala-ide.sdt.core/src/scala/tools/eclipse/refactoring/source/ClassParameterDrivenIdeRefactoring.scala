package scala.tools.eclipse.refactoring.source

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ScalaIdeRefactoring
import scala.tools.refactoring.implementations.ClassParameterDrivenSourceGeneration

import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage

/**
 * Abstract refactoring for common functionality of refactorings that
 * generate code driven by class parameters.
 * @see GenerateHashcodeAndEqualsAction
 * @see IntroduceProductNTraitAction
 */
abstract class ClassParameterDrivenIdeRefactoring(name: String, start: Int, end: Int, sourcefile: ScalaSourceFile)
  extends ScalaIdeRefactoring(name, sourcefile, start, end) {

  val refactoring: ClassParameterDrivenSourceGeneration

  import refactoring.global.ValDef

  private[source] var selectedClassParamNames: List[String] = Nil
  private[source] var callSuper = false
  private[source] var keepExistingEqualityMethods = true

  def refactoringParameters: refactoring.RefactoringParameters =
    refactoring.RefactoringParameters(callSuper, selectByNames(selectedClassParamNames), keepExistingEqualityMethods)

  import refactoring._
  override def getPages: List[RefactoringWizardPage] = preparationResult match {
    case Left(error) => Nil
    case Right(prepResult) => configPage(prepResult)::Nil
  }

  private[source] def configPage(prepResult: refactoring.PreparationResult): RefactoringWizardPage

  import refactoring.global.ValDef
  private def selectByNames(names: List[String]): ValDef => Boolean =
    (param: ValDef) => names.contains(param.name.toString)

}
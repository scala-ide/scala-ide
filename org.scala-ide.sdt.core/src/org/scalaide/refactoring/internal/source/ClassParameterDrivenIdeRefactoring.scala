package org.scalaide.refactoring.internal.source

import scala.tools.refactoring.implementations.ClassParameterDrivenSourceGeneration

import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.refactoring.internal.ScalaIdeRefactoring

/**
 * Abstract refactoring for common functionality of refactorings that
 * generate code driven by class parameters.
 * @see GenerateHashcodeAndEquals
 * @see IntroduceProductNTrait
 */
abstract class ClassParameterDrivenIdeRefactoring(feature: Feature, name: String, start: Int, end: Int, sourcefile: ScalaSourceFile)
  extends ScalaIdeRefactoring(feature, name, sourcefile, start, end) {

  val refactoring: ClassParameterDrivenSourceGeneration

  import refactoring.global.ValDef

  private[source] var selectedClassParamNames: List[String] = Nil
  private[source] var callSuper = false
  private[source] var keepExistingEqualityMethods = true

  def refactoringParameters: refactoring.RefactoringParameters =
    refactoring.RefactoringParameters(callSuper, selectByNames(selectedClassParamNames), keepExistingEqualityMethods)

  override def getPages: List[RefactoringWizardPage] = preparationResult match {
    case Left(error) => Nil
    case Right(prepResult) => configPage(prepResult)::Nil
  }

  private[source] def configPage(prepResult: refactoring.PreparationResult): RefactoringWizardPage

  import refactoring.global.ValDef
  private def selectByNames(names: List[String]): ValDef => Boolean =
    (param: ValDef) => names.contains(param.name.toString)

}

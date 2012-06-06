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
  
  def refactoringParameters = refactoring.RefactoringParameters(callSuper, selectByNames(selectedClassParamNames))

  // The preparation result contains the list of class parameters of the primary constructor
  private[source] lazy val classParamsOrError: Either[refactoring.PreparationError, List[ValDef]] = preparationResult.right.map(_.classParams.map(t => t._1))

  import refactoring._
  override def getPages = classParamsOrError match {
    case Left(error) => Nil
    case Right(classParams) => configPage(classParams)::Nil
  }
  
  private[source] def configPage(classParams: List[ValDef]): RefactoringWizardPage
  
  import refactoring.global.ValDef
  private def selectByNames(names: List[String]): Option[ValDef => Boolean] = 
    Some((param: ValDef) => names.contains(param.name.toString))

}
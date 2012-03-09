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
  
  val project = file.project
  
  val refactoring: ClassParameterDrivenSourceGeneration
  
  var selectedClassParamNames: List[String] = Nil
  var callSuper = false
  var prime = None

  def refactoringParameters = refactoring.RefactoringParameters(callSuper, selectByNames(selectedClassParamNames))

  // The preparation result contains the list of class parameters of the primary constructor
  lazy val classParams = preparationResult.right.get.classParams.map(t => t._1)

  val configPage: RefactoringWizardPage

  override def getPages = configPage :: Nil

  import refactoring.global.ValDef
  def selectByNames(names: List[String]): Option[ValDef => Boolean] = names match {
    case Nil => None
    case _ => Some((param: ValDef) => names.contains(param.name.toString))
  }

}
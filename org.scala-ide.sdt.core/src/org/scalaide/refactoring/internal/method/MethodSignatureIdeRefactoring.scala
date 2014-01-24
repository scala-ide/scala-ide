package scala.tools.eclipse.refactoring.method

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.method.ui.MethodSignatureRefactoringConfigurationPageGenerator
import scala.tools.eclipse.refactoring.Indexed
import scala.tools.eclipse.refactoring.IndexedIdeRefactoring
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MethodSignatureRefactoring
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage

/**
 * Abstract refactoring that contains common functionality of method signature
 * refactorings.
 */
abstract class MethodSignatureIdeRefactoring(refactoringName: String, start: Int, end: Int, file: ScalaSourceFile)
  extends IndexedIdeRefactoring(refactoringName, start, end, file) {

  // Provides the wizard page
  this: MethodSignatureRefactoringConfigurationPageGenerator =>

  trait IndexedMethodSignatureRefactoring extends MethodSignatureRefactoring with GlobalIndexes with Indexed

  val refactoring: IndexedMethodSignatureRefactoring

  type MSRefactoringParameters = refactoring.RefactoringParameters

  // Parameters will be set from the wizard page
  var refactoringParameters: MSRefactoringParameters

  // The selected DefDef that will be refactored
  private[method] def defdefOrError: Either[refactoring.PreparationError, refactoring.global.DefDef] = preparationResult.right.map(_.defdef)

  private[method] def mkConfigPage(defdef: refactoring.global.DefDef, paramsObs: refactoring.RefactoringParameters => Unit): UserInputWizardPage

  override def getPages: List[RefactoringWizardPage] = defdefOrError match {
    case Left(error) => Nil
    case Right(defdef) => mkConfigPage(defdef, refactoringParameters_=)::Nil
  }
}
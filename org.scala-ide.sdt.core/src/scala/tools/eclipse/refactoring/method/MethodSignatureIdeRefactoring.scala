package scala.tools.eclipse.refactoring.method

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.method.ui.MethodSignatureRefactoringConfigurationPageGenerator
import scala.tools.eclipse.refactoring.Indexed
import scala.tools.eclipse.refactoring.IndexedIdeRefactoring
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MethodSignatureRefactoring

import org.eclipse.ltk.ui.refactoring.UserInputWizardPage

/**
 * Abstract refactoring that contains common functionality of method signature
 * refactorings. 
 */
abstract class MethodSignatureScalaIdeRefactoring(refactoringName: String, start: Int, end: Int, file: ScalaSourceFile)
  extends IndexedIdeRefactoring(refactoringName, start, end, file) {

  // Provides the wizard page
  this: MethodSignatureRefactoringConfigurationPageGenerator =>

  trait IndexedMethodSignatureRefactoring extends MethodSignatureRefactoring with GlobalIndexes with Indexed

  val refactoring: IndexedMethodSignatureRefactoring
  
  type MSRefactoringParameters = refactoring.RefactoringParameters

  // Parameters will be set from the wizard page
  var refactoringParameters: MSRefactoringParameters
  
  // The selected DefDef that will be refactored
  private[method] def defdef: refactoring.global.DefDef = preparationResult.right.get.defdef

  // Generates the wizard page
  private def configPage = mkConfigPage(defdef, refactoringParameters_=)
  
  private[method] def mkConfigPage(defdef: refactoring.global.DefDef, paramsObs: refactoring.RefactoringParameters => Unit): UserInputWizardPage

  override def getPages = configPage :: Nil
} 
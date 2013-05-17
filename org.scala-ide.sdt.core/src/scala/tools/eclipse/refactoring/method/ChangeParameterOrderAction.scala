package scala.tools.eclipse.refactoring.method

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.ChangeParamOrder
import scala.tools.eclipse.refactoring.method.ui.ChangeParameterOrderConfigurationPageGenerator

/**
 * A method signature refactoring that changes the order of parameters within
 * their parameter lists.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class ChangeParameterOrderAction extends RefactoringAction {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new ChangeParameterOrderIdeRefactoring(start, end, file)

  class ChangeParameterOrderIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring("Change parameter order", start, end, f) with ChangeParameterOrderConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new ChangeParamOrder with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[List[Int]]()
  }
}
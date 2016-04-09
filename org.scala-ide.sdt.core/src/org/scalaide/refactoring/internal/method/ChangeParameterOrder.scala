package org.scalaide.refactoring.internal
package method

import scala.tools.refactoring.implementations

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.ChangeParameterOrder
import ui.ChangeParameterOrderConfigurationPageGenerator

/**
 * A method signature refactoring that changes the order of parameters within
 * their parameter lists.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class ChangeParameterOrder extends RefactoringExecutorWithWizard {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new ChangeParameterOrderIdeRefactoring(start, end, file)

  class ChangeParameterOrderIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring(ChangeParameterOrder, "Change parameter order", start, end, f) with ChangeParameterOrderConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new implementations.ChangeParamOrder with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[List[Int]]()
  }
}

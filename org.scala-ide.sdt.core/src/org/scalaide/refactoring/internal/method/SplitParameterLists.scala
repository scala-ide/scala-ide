package org.scalaide.refactoring.internal
package method

import scala.tools.refactoring.implementations

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.SplitParameterLists
import ui.SplitParameterListsConfigurationPageGenerator

/**
 * A method signature refactoring that splits parameter lists of a method.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class SplitParameterLists extends RefactoringExecutorWithWizard {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new SplitParameterListsIdeRefactoring(start, end, file)

  class SplitParameterListsIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring(SplitParameterLists, "Split parameter lists", start, end, f) with SplitParameterListsConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new implementations.SplitParameterLists with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[List[Int]]()
  }
}

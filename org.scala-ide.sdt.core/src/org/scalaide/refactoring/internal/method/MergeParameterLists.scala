package org.scalaide.refactoring.internal
package method

import scala.tools.refactoring.implementations

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.MergeParameterLists
import ui.MergeParameterListsConfigurationPageGenerator

/**
 * A method signature refactoring that merges selected parameter lists of a method.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class MergeParameterLists extends RefactoringExecutorWithWizard {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new MergeParameterListsIdeRefactoring(start, end, file)

  class MergeParameterListsIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring(MergeParameterLists, "Merge parameter lists", start, end, f) with MergeParameterListsConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new implementations.MergeParameterLists with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[Int]()
  }
}

package scala.tools.eclipse.refactoring.method

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.method.ui.MergeParameterListsConfigurationPageGenerator
import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.MergeParameterLists

/**
 * A method signature refactoring that merges selected parameter lists of a method.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class MergeParameterListsAction extends RefactoringAction {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new MergeParameterListsIdeRefactoring(start, end, file)

  class MergeParameterListsIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring("Merge parameter lists", start, end, f) with MergeParameterListsConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new MergeParameterLists with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[Int]()
  }
}
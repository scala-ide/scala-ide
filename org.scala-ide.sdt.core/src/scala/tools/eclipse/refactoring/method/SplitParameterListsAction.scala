package scala.tools.eclipse.refactoring.method

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.method.ui.SplitParameterListsConfigurationPageGenerator
import scala.tools.eclipse.refactoring.RefactoringAction
import scala.tools.refactoring.implementations.SplitParameterLists

/**
 * A method signature refactoring that splits parameter lists of a method.
 * The refactoring is applied to the definition of the method as well as to all its usages.
 * This also extends to overridden and partially applied versions of the method.
 */
class SplitParameterListsAction extends RefactoringAction {
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new SplitParameterListsIdeRefactoring(start, end, file)

  class SplitParameterListsIdeRefactoring(start: Int, end: Int, f: ScalaSourceFile)
    extends MethodSignatureIdeRefactoring("Split parameter lists", start, end, f) with SplitParameterListsConfigurationPageGenerator {

    override val refactoring = withCompiler { compiler =>
      new SplitParameterLists with IndexedMethodSignatureRefactoring {
        val global = compiler
      }
    }

    override var refactoringParameters = List[List[Int]]()
  }
}
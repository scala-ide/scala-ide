/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.InlineLocal

/**
 * The Inline Local -- also known as Inline Temp -- refactoring is the dual to Extract Local.
 * It can be used to eliminate a local values by replacing all references to the local value
 * by its right hand side.
 *
 * The implementation does not show a wizard but directly applies the changes (ActionWithNoWizard trait).
 */
class InlineLocalAction extends RefactoringAction with RefactoringActionWithoutWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new InlineLocalScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class InlineLocalScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
      extends ScalaIdeRefactoring("Inline Local", file, start, end) {

    val refactoring = file.withSourceFile((sourceFile, compiler) => new InlineLocal with GlobalIndexes {
      val global = compiler
      val index = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        global.ask(() => GlobalIndex(tree))
      }
    }) getOrElse fail()

    /**
     * The refactoring does not take any parameters.
     */
    def refactoringParameters = new refactoring.RefactoringParameters
  }
}

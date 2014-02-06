package org.scalaide.refactoring.internal

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MoveConstructorToCompanionObject

/**
 * This refactoring redirects calls to the primary constructor to the
 * apply method of the companion object.
 * The apply method and if necessary the companion object will be generated.
 */
class MoveConstructorToCompanionObjectAction extends RefactoringActionWithWizard {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new MoveConstructorToCompanionObjectIdeRefactoring(start, end, file)

  class MoveConstructorToCompanionObjectIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends IndexedIdeRefactoring("Move constructor to companion object", start, end, file) {

    val refactoring = withCompiler { compiler =>
      new MoveConstructorToCompanionObject with GlobalIndexes with Indexed {
        val global = compiler
      }
    }

    val refactoringParameters = new refactoring.RefactoringParameters


    override def indexHints() = {
      val classdef = preparationResult.right.toOption
      classdef.map(_.symbol.nameString).toList
    }
  }
}

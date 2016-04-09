package org.scalaide.refactoring.internal
package rename

import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations

import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.refactoring.internal.RefactoringExecutor
import org.scalaide.refactoring.internal.RefactoringHandler
import org.scalaide.util.eclipse.EditorUtils

/**
 * This implementation supports renaming of all identifiers that occur in the program.
 * For example, local values and variables, method definitions and parameters, class
 * fields, variable bindings in pattern matches, classes, objects, traits, and types parameters.
 *
 * Two different modes are available: inline renaming and a wizard based implementation.
 *
 * Inline renaming is automatically chosen if the identifier that is renamed has only a local scope.
 * For example, a local variable. All names that can potentially be accessed from other compilation
 * units in the program are renamed with the wizard and show a preview of the changes.
 *
 * The actual renaming is done in [[LocalRename]] and [[GlobalRename]].
 */
class Rename extends RefactoringHandler {

  override def perform(): Unit = {
    getRenameRefactoring.perform()
  }

  def getRenameRefactoring: RefactoringExecutor =
    if (isLocalRename) new LocalRename else new GlobalRename

  /**
   * Using the currently opened file and selection, determines whether the
   * selected SymbolTree is only locally visible or not.
   */
  private def isLocalRename: Boolean = {

    val isLocalRename = EditorUtils.withScalaFileAndSelection { (scalaFile, selected) =>
      scalaFile.withSourceFile{(source, compiler) =>
        val refactoring = new implementations.Rename with GlobalIndexes {
          override val global = compiler
          override val index = EmptyIndex
        }

        val selection = refactoring.askLoadedAndTypedTreeForFile(source).left.toOption map { tree =>
          val start = selected.getOffset
          val end = start + selected.getLength
          new refactoring.FileSelection(source.file, tree, start, end)
        }

        val preparation = selection flatMap { selection =>
          compiler.asyncExec(refactoring.prepare(selection)).getOption()
        }

        preparation flatMap (_.right.toOption) map {
          case refactoring.PreparationResult(_, isLocal) => isLocal
          case _ => false
        }
      }
    }.flatten getOrElse false

    isLocalRename
  }
}

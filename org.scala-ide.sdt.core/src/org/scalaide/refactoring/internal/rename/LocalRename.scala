package org.scalaide.refactoring.internal
package rename

import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.LocalRename
import org.scalaide.util.eclipse.EditorUtils
import scala.tools.refactoring.common.TextChange

/**
 * Supports renaming of identifiers inside a single file using Eclipse's
 * Linked UI Mode.
 *
 * Should the renaming fail to properly initialize, the GlobalRenameAction
 * is called which then shows the error message.
 */
class LocalRename extends RefactoringExecutor {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new RenameScalaIdeRefactoring(start, end, file)

  class RenameScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
      extends ScalaIdeRefactoring(LocalRename, "Inline Rename", file, start, end) {

    def refactoringParameters = ""

    val refactoring = file.withSourceFile((source, compiler) => new implementations.Rename with GlobalIndexes {
      val global = compiler
      var index = {
        val tree = askLoadedAndTypedTreeForFile(source).left.get
        global.ask(() => GlobalIndex(tree))
      }
    }) getOrElse fail()
  }

  override def perform(): Unit = {

    def runInlineRename(r: RenameScalaIdeRefactoring): Unit = {
      import r.refactoring._

      val positions = global.ask(() => r.performRefactoring()).collect { case change: TextChange =>
        (change.from, change.to - change.from)
      }

      EditorUtils.enterLinkedModeUi(positions, selectFirst = false)
    }

    createScalaIdeRefactoringForCurrentEditorAndSelection() map {
      case refactoring: RenameScalaIdeRefactoring =>
        refactoring.preparationResult.fold(_ => (new GlobalRename).perform(), _ => runInlineRename(refactoring))
    }
  }
}

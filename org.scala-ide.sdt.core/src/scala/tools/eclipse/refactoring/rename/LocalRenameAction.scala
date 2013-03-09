/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring
package rename

import org.eclipse.jface.action.IAction
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.Rename

/**
 * Supports renaming of identifiers inside a singe file using Eclipse's
 * Linked UI Mode.
 *
 * Should the renaming fail to properly initialize, the GlobalRenameAction
 * is called which then shows the error message.
 */
class LocalRenameAction extends RefactoringAction {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new RenameScalaIdeRefactoring(start, end, file)

  class RenameScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Inline Rename", file, start, end) {

    def refactoringParameters = ""

    val refactoring = file.withSourceFile((file, compiler) => new Rename with GlobalIndexes {
      val global = compiler
      var index = {
        val tree = askLoadedAndTypedTreeForFile(file).left.get
        global.ask(() => GlobalIndex(tree))
      }
    })()
  }

  override def run(action: IAction) {

    def runInlineRename(r: RenameScalaIdeRefactoring) {
      import r.refactoring._
      val selectedSymbolTree = r.selection().selectedSymbolTree

      val positions = for {
        // there's always a selected tree, otherwise
        // the refactoring won't be called.
        selected <- selectedSymbolTree.toList
        t <- index.occurences(selected.symbol)
      } yield {
        val pos = t match {
          case ImportSelectorTree(name, global.EmptyTree) =>
            name.pos
          case ImportSelectorTree(_, rename) =>
            rename.pos
          case t =>
            t.namePosition
        }
        if(pos.source.content(pos.start) == '`') {
          (pos.start + 1, pos.end - pos.start - 2)
        } else {
          (pos.start, pos.end - pos.start)
        }
      }

      EditorHelpers.enterLinkedModeUi(positions)
    }

    createScalaIdeRefactoringForCurrentEditorAndSelection() map {
      case refactoring: RenameScalaIdeRefactoring =>
        refactoring.preparationResult.fold(_ => (new GlobalRenameAction).run(action), _ => runInlineRename(refactoring))
    }
  }
}

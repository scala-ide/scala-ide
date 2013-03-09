/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring
package rename

import org.eclipse.jface.action.IAction
import javaelements.ScalaSourceFile
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.{ GlobalIndexes, Indexes }
import scala.tools.refactoring.common.{ ConsoleTracing, InteractiveScalaCompiler, Selections }
import scala.tools.refactoring.implementations.Rename

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
 * The actual renaming is done in LocalRenameAction and GlobalRenameAction.
 */
class RenameAction extends ActionAdapter {

  override def run(action: IAction) {
    val renameAction = getRenameAction
    renameAction.run(action)
  }

  def getRenameAction = if (isLocalRename) new LocalRenameAction else new GlobalRenameAction

  /**
   * Using the currently opened file and selection, determines whether the
   * selected SymbolTree is only locally visible or not.
   */
  private def isLocalRename: Boolean = {

    val isLocalRename = EditorHelpers.withScalaFileAndSelection { (scalaFile, selected) =>
      scalaFile.withSourceFile{(file, compiler) =>
        val refactoring = new Rename with GlobalIndexes {
          val global = compiler
          val index = EmptyIndex
        }

        val selection = refactoring.askLoadedAndTypedTreeForFile(file).left.toOption map { tree =>
          val start = selected.getOffset
          val end = start + selected.getLength
          new refactoring.FileSelection(file.file, tree, start, end)
        }

        selection map refactoring.prepare flatMap (_.right.toOption) map {
          case refactoring.PreparationResult(_, isLocal) => isLocal
          case _ => false
        }
      }()
    } getOrElse false

    isLocalRename
  }
}

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

    def getCurrentSelection: Option[Selections#FileSelection] = {
      EditorHelpers.withScalaFileAndSelection { (scalaFile, selected) =>

        val selection = scalaFile.withSourceFile { (file, compiler) =>
          val selections = new Selections with InteractiveScalaCompiler {
            val global = compiler
          }

          val start = selected.getOffset
          val end = start + selected.getLength

          selections.askLoadedAndTypedTreeForFile(file).left.map { tree =>
            new selections.FileSelection(file.file, tree, start, end)
          }
        }()

        Some(selection)
      } flatMap (_.left.toOption)
    }

    getCurrentSelection flatMap { selection =>
      selection.selectedSymbolTree map { t =>
        // a very simplistic check if the symbol we want to rename is only locally visible
        (t.symbol.isPrivate || t.symbol.isLocal) && !t.symbol.hasFlag(Flags.ACCESSOR)
      }
    } getOrElse false
  }
}

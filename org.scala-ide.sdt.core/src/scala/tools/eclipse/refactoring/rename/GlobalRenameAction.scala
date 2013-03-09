/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring
package rename

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui._
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.{GlobalIndexes, Indexes, NameValidation}
import scala.tools.refactoring.common.{ConsoleTracing, InteractiveScalaCompiler, Selections}
import scala.tools.refactoring.implementations.Rename
import scala.tools.refactoring.Refactoring

/**
 * Renames using a wizard and a change preview. This action is used
 * for all global rename refactorings and also from the RenameParticipant.
 *
 * When a class is renamed that has the same name as the source file,
 * the file is renamed too.
 */
class GlobalRenameAction extends RefactoringAction {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new RenameScalaIdeRefactoring(start, end, file)

  /**
   * The actual refactoring instance that is used by the RefactoringAction.
   */
  class RenameScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Rename", file, start, end) with FullProjectIndex {

    val project = file.scalaProject

    var name = ""

    def refactoringParameters = name

    val refactoring = withCompiler { compiler =>
      new Rename with GlobalIndexes with NameValidation {
        val global = compiler

        /* The initial index is empty, it will be filled during the initialization
         * where we can show a progress bar and let the user cancel the operation.*/
        var index = EmptyIndex
      }
    }

    /**
     * A cleanup handler, will later be set by the refactoring
     * to remove all loaded compilation units from the compiler.
     */
    var cleanup = () => ()

    /* (non-Javadoc)
     * @see scala.tools.eclipse.refactoring.ScalaIdeRefactoring#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
     */
    override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = {

      val status = super.checkInitialConditions(pm)

      if(!status.hasError) {

        val selectedSymbol = preparationResult.right.get.selectedTree.symbol // only reachable if it's a Right value

        name = selectedSymbol match {
          case sym if sym.isSetter => sym.getter(sym.owner).nameString
          case sym => sym.nameString
        }

        val (index, cleanupHandler) = buildFullProjectIndex(pm, name :: Nil)

        refactoring.index = index

        // will be called after the refactoring has finished
        cleanup = cleanupHandler
      }

      if(pm.isCanceled) {
        status.addWarning("Indexing was cancelled, types will not be renamed.")
      }

      status
    }

    override def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
      val status = super.checkFinalConditions(pm)

      val selectedSymbol = selection().selectedSymbolTree map (_.symbol) getOrElse refactoring.global.NoSymbol

      refactoring.global.askOption { () =>
        refactoring.doesNameCollide(name, selectedSymbol)
      } map {
        case Nil => ()
        case collisions =>
          val names = collisions map (s => s.fullName) mkString ", "
          status.addWarning("The name \""+ name +"\" is already in use: "+ names)
      }

      status
    }

    override def getPages = new NewNameWizardPage((s => name = s), refactoring.isValidIdentifier, name, "refactoring_rename") :: Nil

    override def createChange(pm: IProgressMonitor) = {
      val compositeChange = super.createChange(pm)

      preparationResult.right.get.selectedTree match {
        case impl: refactoring.global.ImplDef if impl.name.toString + ".scala" == file.file.name =>
          file.getCorrespondingResource match {
            case ifile: IFile =>
              compositeChange.add(new RenameResourceChange(ifile.getFullPath, name + ".scala"))
            case _ =>
          }
        case _ =>
      }

      cleanup()

      compositeChange
    }
  }
}

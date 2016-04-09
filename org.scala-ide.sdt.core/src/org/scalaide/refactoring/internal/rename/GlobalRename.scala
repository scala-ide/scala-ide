package org.scalaide.refactoring.internal
package rename

import scala.language.reflectiveCalls
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.analysis.NameValidation
import scala.tools.refactoring.implementations

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.GlobalRename
import org.scalaide.refactoring.internal.ui.NewNameWizardPage

/**
 * This refactoring is used for all global rename refactorings and also from
 * the RenameParticipant.
 *
 * When a class is renamed that has the same name as the source file,
 * the file is renamed too.
 */
class GlobalRename extends RefactoringExecutorWithWizard {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new RenameScalaIdeRefactoring(start, end, file)

  class RenameScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring(GlobalRename, "Rename", file, start, end) with FullProjectIndex {

    val project = file.scalaProject

    var name = ""

    def refactoringParameters = name

    val refactoring = withCompiler { compiler =>
      new implementations.Rename with GlobalIndexes with NameValidation {
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

    override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = {

      val status = super.checkInitialConditions(pm)

      if(!status.hasError) {

        val selectedSymbol = preparationResult.right.get.selectedTree.symbol // only reachable if it's a Right value

        name = selectedSymbol match {
          case sym if sym.isSetter => sym.getterIn(sym.owner).nameString
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

      refactoring.global.asyncExec {
        refactoring.doesNameCollide(name, selectedSymbol)
      } getOption() map {
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
      cleanup()
      compositeChange
    }
  }
}

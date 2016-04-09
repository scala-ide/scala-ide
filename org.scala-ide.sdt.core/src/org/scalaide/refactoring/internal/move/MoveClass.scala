package org.scalaide.refactoring.internal
package move

import scala.language.reflectiveCalls
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.NewFileChange
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IPackageFragment
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateFileChange
import org.eclipse.ltk.core.refactoring.CompositeChange
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.core.refactoring.resource.MoveResourceChange
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.MoveClass

/**
 * The Move Class refactoring moves (non-nested) classes, objects and traits between different
 * packages. In files that contains multiple classes, it's possible to split off a class and
 * move it to its own file.
 *
 * Using the ScalaMoveParticipant, the refactoring also hooks into other Eclipse move actions,
 * like for example when a file is moved by drag&drop in the package explorer.
 */
class MoveClass extends RefactoringExecutorWithWizard {

  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new MoveClassScalaIdeRefactoring(start, end, file)

  class MoveClassScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
      extends ScalaIdeRefactoring(MoveClass, "Move Class/Trait/Object", file, start, end) with FullProjectIndex {

    val project = file.scalaProject

    var moveSingleImpl: refactoring.PreparationResult = None

    var target: IPackageFragment = null

    def refactoringParameters = refactoring.RefactoringParameters(target.getElementName, moveSingleImpl)

    def setMoveSingleImpl(moveSingle: Boolean): Unit = {
      if(moveSingle && preparationResult.isRight) {
        // the function is never called if we don't have a value:
        moveSingleImpl = preparationResult.right.get
      } else {
        moveSingleImpl = None
      }
    }

    val refactoring = withCompiler { compiler =>
      new implementations.MoveClass with GlobalIndexes {
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

      // Call this early so that we can access the preparationresult
      val status = super.checkInitialConditions(pm)

      if(pm.isCanceled) {
        status.addWarning("Indexing was cancelled, this might lead to undesired results. Please review the changes carefully!")
      }

      status
    }

    override def createChange(pm: IProgressMonitor): CompositeChange = {

      val (textChanges, newFileChanges) = createRefactoringChanges(pm)

      new CompositeChange(getName) {

        scalaChangesToEclipseChanges(textChanges) foreach add

        newFileChanges match {

          // If there's no new file to create, we move the current file.
          case Nil =>
            add(new MoveResourceChange(file.getResource, target.getCorrespondingResource.asInstanceOf[IFolder]))

          // Otherwise, create a new file with the changes's content.
          case newFile :: rest =>
            val pth = target.getPath.append(preparationResult.right.get.get.name.toString + ".scala")
            add(new CreateFileChange(pth, newFile.text, file.getResource.asInstanceOf[IFile].getCharset))
        }
      }
    }

    override def getPages = {
      val selectedImpl = preparationResult.right.toOption flatMap (_.map(_.name.toString))
      List(new ui.MoveClassRefactoringConfigurationPage(file.getResource(), selectedImpl, target_=, setMoveSingleImpl))
    }

    private[move] def createRefactoringChanges(pm: IProgressMonitor) = {

      val (index, cleanupHandler) = {
        val toMove = refactoring.statsToMove(selection(), refactoringParameters) collect {
          case impl: refactoring.global.ImplDef => impl.name.toString
        }
        buildFullProjectIndex(pm, toMove)
      }

      refactoring.index = index

      // will be called after the refactoring has finished
      cleanup = cleanupHandler

      // the changes contain either a NewFileChange or we need to move the current file.

      val (textChanges, newFileChanges) = {
        (performRefactoring() :\ (List[TextChange](), List[NewFileChange]())) {
          case (change: TextChange, (textChanges, newFiles)) =>
            (change :: textChanges, newFiles)
          case (change: NewFileChange, (textChanges, newFilesChanges)) =>
            (textChanges, change :: newFilesChanges)
          case (unexpected, _) =>
            throw new AssertionError(s"Unexpected change $unexpected; please make sure that 'MoveClass' in scalaide and 'MoveClass' in the refactoring library are in sync")
        }
      }

      cleanup()

      (textChanges, newFileChanges)
    }
  }
}

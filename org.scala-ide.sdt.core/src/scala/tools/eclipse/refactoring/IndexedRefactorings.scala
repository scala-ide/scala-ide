package scala.tools.eclipse.refactoring

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.MultiStageRefactoring
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import scala.tools.eclipse.ScalaProject

/**
 * Helper trait that adds an index variable to a refactoring.
 * Needed to be able to factor out common functionality of refactorings
 * that need an index of the full project.
 * @see IndexedIdeRefactoring
 */
trait Indexed {
  this: GlobalIndexes =>
  var index = EmptyIndex
}

/**
 * Abstract ScalaIdeRefactoring for refactorings that need an index of the full project.
 */
abstract class IndexedIdeRefactoring(refactoringName: String, start: Int, end: Int, sourcefile: ScalaSourceFile)
  extends ScalaIdeRefactoring(refactoringName, sourcefile, start, end) with FullProjectIndex {

  val project: ScalaProject = sourcefile.scalaProject

  val refactoring: MultiStageRefactoring with GlobalIndexes with Indexed

  /**
   * A cleanup handler, will later be set by the refactoring
   * to remove all loaded compilation units from the compiler.
   */
  var cleanup: () => Unit = () => ()

  override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = {
    val status = super.checkInitialConditions(pm)

    if (!status.hasError) {
      val (index, cleanupIndex) = buildFullProjectIndex(pm, indexHints)
      refactoring.index = index
      // will be called after the refactoring has finished
      cleanup = cleanupIndex
    }

    if (pm.isCanceled) {
      status.addWarning("Indexing was cancelled, aborting refactoring.")
    }

    status
  }

  /**
   * Provide hints for index building.
   * If no hints are provided, the full project index is built,
   * this can slow down the refactoring considerably.
   */
  def indexHints(): List[String] = Nil

  override def createChange(pm: IProgressMonitor) = {
    val change = super.createChange(pm)
    cleanup()
    change
  }

}
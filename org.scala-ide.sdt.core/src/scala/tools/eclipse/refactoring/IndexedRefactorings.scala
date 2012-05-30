package scala.tools.eclipse.refactoring

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.MultiStageRefactoring

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.RefactoringStatus

/**
 * Helper trait that adds an index variable to a refactoring. 
 * Needed to be able to factor out common functionality of refactorings
 * that need an index of the full project.
 * @see IndexedIdeRefactoring
 */
trait Indexed {
  this: GlobalIndexes =>
  var index = GlobalIndex(Nil)
}

/**
 * Abstract ScalaIdeRefactoring for refactorings that need an index of the full project.
 */
abstract class IndexedIdeRefactoring(refactoringName: String, start: Int, end: Int, sourcefile: ScalaSourceFile) 
  extends ScalaIdeRefactoring(refactoringName, sourcefile, start, end) with FullProjectIndex {

  val project = sourcefile.project
  
  val refactoring: MultiStageRefactoring with GlobalIndexes with Indexed
  
  /**
   * A cleanup handler, will later be set by the refactoring
   * to remove all loaded compilation units from the compiler.
   */
  var cleanup = () => ()

  override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = {
    val status = super.checkInitialConditions(pm)

    if (!status.hasError) { 
      // the hints parameter is not used, this can slow down the refactoring considerably!
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
   */
  def indexHints(): List[String] = Nil

  override def createChange(pm: IProgressMonitor) = {
    val change = super.createChange(pm)
    cleanup()
    change
  }

}
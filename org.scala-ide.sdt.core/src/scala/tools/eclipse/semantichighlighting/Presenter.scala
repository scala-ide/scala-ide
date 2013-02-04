package scala.tools.eclipse.semantichighlighting

import scala.collection.immutable
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolClassification
import scala.tools.eclipse.semantichighlighting.classifier.SymbolInfo
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.ISourceViewer

/** This class is responsible of coordinating the correct initialization of the different components needed to perform semantic highlighting.
  *
  * Specifically, a reconciler job is created so that the symbols' classification is performed asynchronously wrt to the component that
  * triggers sematic highlighting (which, in its current state, is the `JavaReconciler`. Have a look at
  * `scala.tools.eclipse.semantichighlighting.ui.ScalaTextPresentationProxy` for the details of how semantic highlighting is hooked in the
  * java reconciler).
  *
  * This class is thread-safe.
  *
  * @param editorProxy Responsible of updating the text presentation in the editor and responds to reconciliations events.
  * @param positionFactory Factory for semantically highlighted positions.
  * @param preferences Semantic Highlighting user's preferences.
  */
class Presenter(editorProxy: TextPresentationHighlighter with InteractiveCompilationUnitEditor, positionsFactory: Presenter.PositionsFactory, preferences: Preferences) {

  private val reconciler = {
    val job = new Reconciler(editorProxy.getInteractiveCompilationUnit)
    job.setPriority(Job.DECORATE)
    job
  }

  /** Keep tracks of document's events and responsible for updating the highlighted position in the document.*/
  private val documentProxy = new DocumentProxy(editorProxy.sourceViewer, reconciler, getPositionCategory)

  /** True if method `initialize` was called, false otherwise.
    * Guarded by initializationLock
    */
  private var initialized: Boolean = false

  /** Lock used to protect initialization/disposal of this instance. */
  private val initializationLock: AnyRef = new Object

  /** Should be called right after creating an instance of `this` class. */
  def initialize(): Unit = initializationLock.synchronized {
    try {
      documentProxy.initialize()
      editorProxy.initialize(reconciler, getPositionCategory)
    } finally {
      initialized = true
    }
  }

  /** Stop any reconciling job that may be currently executing and unregister all editor/document's listeners. */
  def dispose(): Unit = initializationLock.synchronized {
    if (initialized) {
      try {
        reconciler.cancel()
        documentProxy.dispose()
        editorProxy.dispose()
      } finally {
        initialized = false
      }
    }
  }

  /** Returns a different category for each opened editor (because a different instance of `this` class is
    * created for each opened editor). The reason for this is that the the document's model holds a map from
    * categories to positions, hence for performance reasons (i.e., reducing the number of positions to check
    * when the document is changed) it makes sense to have a different categories for each opened editor.
    */
  private def getPositionCategory: String = toString

  /** A background job that performs semantic highlighting.
    *
    * This class is thread-safe.
    */
  private class Reconciler(scu: InteractiveCompilationUnit) extends Job("semantic highlighting") with HasLogger {

    override def run(monitor: IProgressMonitor): IStatus = {
      scu.withSourceFile { (sourceFile, compiler) =>
        val symbolInfos =
          try new SymbolClassification(sourceFile, compiler, preferences.isUseSyntacticHintsEnabled()).classifySymbols(monitor)
          catch {
            case e =>
              logger.error("Error performing semantic highlighting", e)
              Nil
          }
        if (monitor.isCanceled()) Status.CANCEL_STATUS
        else {
          val positions = positionsFactory(symbolInfos)
          val sortedPositions = positions.toList.sorted(Presenter.PositionsByOffset)
          val positionsChange = documentProxy.createDocumentPositionsChange(sortedPositions)

          if (monitor.isCanceled()) Status.CANCEL_STATUS
          else positionsChange map (updateTextPresentation(_)) getOrElse Status.OK_STATUS
        }
      }(Status.OK_STATUS)
    }

    private def updateTextPresentation(positionsChange: DocumentPositionsChange): IStatus = {
      val damage = positionsChange.createRegionChange()
      if (damage.getLength > 0) {
        documentProxy.updateDocumentPositions(positionsChange)
        editorProxy.updateTextPresentation(damage)
      }
      else Status.OK_STATUS
    }
  }
}

object Presenter {
  object PositionsByOffset extends Ordering[Position] {
    override def compare(x: Position, y: Position): Int = x.getOffset() - y.getOffset()
  }

  type PositionsFactory = List[SymbolInfo] => immutable.HashSet[Position]
}
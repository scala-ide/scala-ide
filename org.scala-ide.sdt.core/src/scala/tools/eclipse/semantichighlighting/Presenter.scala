package scala.tools.eclipse.semantichighlighting

import scala.collection.immutable
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolClassification
import scala.tools.eclipse.semantichighlighting.classifier.SymbolInfo

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.IPositionUpdater
import org.eclipse.jface.text.ITextInputListener
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.ISourceViewer

/** This class is responsible of coordinating the correct initialization of the different components needed to perform semantic highlighting.
  *
  * Specifically, a reconciler job is created so that the symbols' classification is performed asynchronously with respect to the component
  * that triggers semantic highlighting (which, in its current state, is the [[org.eclipse.jdt.internal.ui.text.JavaReconciler]]. Have a look
  * at [[scala.tools.eclipse.semantichighlighting.ui.ScalaTextPresentationProxy]] for the details of how semantic highlighting is hooked in
  * the java reconciler).
  *
  * @note This class is thread-safe.
  *
  * @param unit             The compilation unit on which semantic highlighting is performed.
  * @param textPresentation Responsible of updating the text presentation in the editor and responds to reconciliations events.
  * @param positionFactory  Factory for (semantically) highlighted positions.
  * @param preferences      Semantic highlighting user's preferences.
  */
class Presenter(unit: InteractiveCompilationUnit, textPresentation: TextPresentationHighlighter, positionsFactory: Presenter.PositionsFactory, preferences: Preferences) extends HasLogger {

  private val reconciler = {
    val job = new Reconciler(unit)
    job.setPriority(Job.DECORATE)
    job
  }

  /** Responsible for updating the highlighted position in the document.*/
  private val documentPositions = new DocumentPositions(textPresentation, getPositionCategory)

  private val documentInputChangeListener = new Presenter.DefaultTextInputListener(this, reconciler)
  private val documentPositionsUpdater = new Presenter.HighlightedPositionUpdater(documentPositions)
  private val documentChangeListener = new Presenter.DefaultDocumentListener(reconciler)

  /** Prevents `this` instance to be initialized more than once.
    *
    * @note Guarded by `initializationLock`
    * @return `true` if method `initialize` was called, `false` otherwise.
    */
  private var initialized: Boolean = false

  /** Lock used to protect initialization/disposal of this instance. */
  private val initializationLock: AnyRef = new Object

  /** Should be called right after creating an instance of `this` class. */
  def initialize(): Unit = initializationLock.synchronized {
    // sanity check
    if (initialized)
      throw new IllegalStateException("Attempted to initialize twice instance " + this +
        ". This is not supported, even if the instance was previously disposed. Create a new instance instead.")

    try {
      documentPositions.start()
      withSourceViewer { sourceViewer =>
        sourceViewer.addTextInputListener(documentInputChangeListener)
        manageDocument(sourceViewer.getDocument)
      }
      textPresentation.initialize(reconciler, getPositionCategory)
    }
    finally initialized = true
  }

  /** Stop any reconciling job that may be currently executing and unregister all editor/document's listeners. */
  def dispose(): Unit = initializationLock.synchronized {
    if (initialized) {
      documentPositions ! DocumentPositions.PoisonPill
      reconciler.cancel()
      withSourceViewer { sourceViewer =>
        sourceViewer.removeTextInputListener(documentInputChangeListener)
        releaseDocument(sourceViewer.getDocument())
      }
      textPresentation.dispose()
    }
    else logger.debug("Calling `dispose` on a uninitialized " + this + " has no effect.")
  }

  private def manageDocument(document: IDocument): Unit = {
    if (document != null) {
      document.addPositionCategory(getPositionCategory)
      document.addPositionUpdater(documentPositionsUpdater)
      document.addDocumentListener(documentChangeListener)
    }
  }

  private def releaseDocument(document: IDocument): Unit = {
    if (document != null) {
      document.removePositionUpdater(documentPositionsUpdater)
      document.removeDocumentListener(documentChangeListener)
    }
  }

  private def withSourceViewer(f: ISourceViewer => Unit): Unit = Option(textPresentation.sourceViewer) foreach f

  /** Returns a different category for each opened editor (because a different instance of `this` class is
    * created for each opened editor).
    *
    * @note The reason for returning a different category for each editor is that the the document's model
    *     holds a map from categories to positions, hence for performance reasons (i.e., reducing the number
    *     of positions to check when the document is changed) it makes sense to have a different categories for
    *     each opened editor.
    */
  private def getPositionCategory: String = toString

  /** A background job that performs semantic highlighting.
    *
    * @note This class is thread-safe.
    * @note The Eclipse framework ensures that the same `Job` instance can't be running in parallel, i.e.,
    *    a `Job` instance can be re-executed only after the currently running one has completed.
    */
  private class Reconciler(scu: InteractiveCompilationUnit) extends Job("semantic highlighting") with HasLogger {

    override def run(monitor: IProgressMonitor): IStatus = doSemanticHighlighting(monitor)

    private def doSemanticHighlighting(monitor: IProgressMonitor): IStatus = {
      scu.withSourceFile { (sourceFile, compiler) =>
        val symbolInfos =
          try new SymbolClassification(sourceFile, compiler, preferences.isUseSyntacticHintsEnabled()).classifySymbols(monitor)
          catch {
            case e: Exception =>
              logger.error("Error while performing semantic highlighting.", e)
              Nil
          }
        if (monitor.isCanceled()) Status.CANCEL_STATUS
        else {
          val newPositions = positionsFactory(symbolInfos)
          val sortedNewPositions = newPositions.toList.sorted(Presenter.PositionsByOffset)

          if (monitor.isCanceled()) Status.CANCEL_STATUS
          else {
            documentPositions ! DocumentPositions.UpdatePositions(monitor, sortedNewPositions)
            Status.OK_STATUS
          }
        }
      }(Status.OK_STATUS)
    }
  }
}

object Presenter {
  object PositionsByOffset extends Ordering[Position] {
    override def compare(x: Position, y: Position): Int = x.getOffset() - y.getOffset()
  }

  type PositionsFactory = List[SymbolInfo] => immutable.HashSet[Position]

  private class DefaultTextInputListener(presenter: Presenter, reconciler: Job) extends ITextInputListener {
    override def inputDocumentAboutToBeChanged(oldInput: IDocument, newInput: IDocument): Unit = {
      reconciler.cancel()
      presenter.releaseDocument(oldInput)
    }
    override def inputDocumentChanged(oldInput: IDocument, newInput: IDocument): Unit =
      presenter.manageDocument(newInput)
  }

  private class DefaultDocumentListener(reconciler: Job) extends IDocumentListener {
    override def documentAboutToBeChanged(event: DocumentEvent): Unit = reconciler.cancel()
    override def documentChanged(event: DocumentEvent): Unit = {}
  }

  private class HighlightedPositionUpdater(documentPositions: DocumentPositions) extends IPositionUpdater with HasLogger {
    override def update(event: DocumentEvent): Unit =
      documentPositions ! DocumentPositions.DocumentChanged(event.getOffset, event.getLength, event.getText())
  }
}
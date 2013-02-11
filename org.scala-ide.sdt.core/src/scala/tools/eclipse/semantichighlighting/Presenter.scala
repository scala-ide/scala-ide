package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolClassification
import scala.tools.eclipse.ui.UIThread

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.IPositionUpdater
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextInputListener

/** This class is responsible of coordinating the correct initialization of the different components
  * needed to perform semantic highlighting.
  *
  * @note This class is thread-safe.
  *
  * @param compilationUnit         The compilation unit on which semantic highlighting is performed.
  * @param presentationHighlighter Responsible of updating the editor's text presentation.
  * @param preferences             Semantic Highlighting user's preferences.
  * @param uiThread                Allows to run code in the UI Thread.
  */
class Presenter(
  compilationUnit: InteractiveCompilationUnit,
  presentationHighlighter: TextPresentationHighlighter,
  preferences: Preferences,
  uiThread: UIThread) extends HasLogger { self =>
  import Presenter._

  private val reconciler = {
    val job = new Reconciler(compilationUnit)
    // job.setSystem(true) // TODO: uncomment this once we trust the reconciler job can't be lost 
    job.setPriority(Job.DECORATE)
    job
  }

  /** Keep tracks of the highlighted positions in the editor.*/
  private val positionsTracker = new PositionsTracker

  private val documentSwapListener = new DocumentSwapListener(self, reconciler)
  private val documentContentListener = new DocumentContentListener(reconciler)
  private val positionUpdater = new PositionUpdater(positionsTracker)

  /** Should be called right after creating an instance of `this` class. 
   *  
   *  @param forceRefresh Force a semantic reconciler run during initialization.
   */
  def initialize(forceRefresh: Boolean): Unit = {
    presentationHighlighter.initialize(reconciler, positionsTracker)
    Option(presentationHighlighter.sourceViewer) foreach { sv =>
      sv.addTextInputListener(documentSwapListener)
      manageDocument(sv.getDocument)
    }
    if(forceRefresh) refresh()
  }

  private def manageDocument(document: IDocument): Unit = {
    if (document != null) {
      document.addPositionUpdater(positionUpdater)
      document.addDocumentListener(documentContentListener)
    }
  }

  private def releaseDocument(document: IDocument): Unit = {
    if (document != null) {
      document.removePositionUpdater(positionUpdater)
      document.removeDocumentListener(documentContentListener)
    }
  }

  /** Stop any reconciling job that may be currently executing and unregister all 
   *  editor/document's listeners. 
   *
   *  @param removesHighlights Force removal of highlightes postions in the editor.  
   */
  def dispose(removesHighlights: Boolean): Unit = {
    reconciler.cancel()
    // invalidate the text presentation before disposing `presentationHighlighter` (because it 
    // contains the logic for applying the styles).
    if (removesHighlights) removesAllHighlightings()
    presentationHighlighter.dispose()
    Option(presentationHighlighter.sourceViewer) foreach { sv => releaseDocument(sv.getDocument) }
  }

  /** Refresh the highlightings */
  private def refresh(): Unit = { reconciler.schedule() }

  /** Removes all highlightings.
   *  @note Must be called from within the UI Thread
   */
  private def removesAllHighlightings(): Unit = {
    positionsTracker.dispose()
    Option(presentationHighlighter.sourceViewer) foreach (_.invalidateTextPresentation())
  }

  /** A background job that performs semantic highlighting.
    *
    * @note This class is thread-safe.
    */
  private class Reconciler(scu: InteractiveCompilationUnit) extends Job("semantic highlighting") with HasLogger {

    override def run(monitor: IProgressMonitor): IStatus = {
      if (monitor.isCanceled()) Status.CANCEL_STATUS
      else performSemanticHighlighting(monitor)
    }

    private def performSemanticHighlighting(monitor: IProgressMonitor): IStatus = {
      scu.withSourceFile { (sourceFile, compiler) =>
        positionsTracker.startComputingNewPositions()
        val symbolInfos =
          try new SymbolClassification(sourceFile, compiler, preferences.isUseSyntacticHintsEnabled()).classifySymbols(monitor)
          catch {
            case e: Exception =>
              logger.error("Error performing semantic highlighting", e)
              Nil
          }
        val newPositions = Position.from(symbolInfos)
        val positionsChange = positionsTracker.createPositionsChange(newPositions)
        val damagedRegion = positionsChange.affectedRegion()

        // if the positions held by the `positionsTracker` have changed, then 
        // it's useless to proceed because the `newPositions` have computed on a  
        // not up-to-date compilation unit. Let the next reconciler run take care 
        // of re-computing the positions with the up-to-date content.
        if (damagedRegion.getLength > 0 && !positionsTracker.isPositionsChanged) {
          val sortedPositions = newPositions.sorted.toArray
          runPositionsUpdateInUiThread(sortedPositions, damagedRegion)
          Job.ASYNC_FINISH
        }
        else Status.OK_STATUS
      }(Status.OK_STATUS)
    }

    private def runPositionsUpdateInUiThread(newPositions: Array[Position], damagedRegion: IRegion): Unit = uiThread.asyncExec {
      try {
        setThread(uiThread.get)
        if (!positionsTracker.isPositionsChanged) {
          positionsTracker.swapPositions(newPositions)
          presentationHighlighter.updateTextPresentation(damagedRegion)
        }
      }
      catch { case e: Exception => () }
      finally done(Status.OK_STATUS)
    }
  }
}

private object Presenter {
  class DocumentSwapListener(presenter: Presenter, reconciler: Job) extends ITextInputListener {
    override def inputDocumentAboutToBeChanged(oldInput: IDocument, newInput: IDocument): Unit = {
      reconciler.cancel()
      presenter.releaseDocument(oldInput)
    }
    override def inputDocumentChanged(oldInput: IDocument, newInput: IDocument): Unit =
      presenter.manageDocument(newInput)
  }

  class DocumentContentListener(reconciler: Job) extends IDocumentListener {
    override def documentAboutToBeChanged(event: DocumentEvent): Unit = reconciler.cancel()
    override def documentChanged(event: DocumentEvent): Unit = ()
  }

  class PositionUpdater(positionsTracker: PositionsTracker) extends IPositionUpdater with HasLogger {
    override def update(event: DocumentEvent): Unit =
      positionsTracker.updatePositions(event)
  }
}
package org.scalaide.ui.internal.editor.decorators.semantichighlighting

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
import org.scalaide.core.internal.decorators.semantichighlighting.Position
import org.scalaide.core.internal.decorators.semantichighlighting.PositionsTracker
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolClassification
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.editor.InteractiveCompilationUnitEditor
import org.scalaide.util.internal.ui.UIThread

/** This class is responsible of coordinating the correct initialization of the different components
  * needed to perform semantic highlighting in an editor.
  *
  * @note This class is thread-safe.
  *
  * @param editor                  The editor holding the unit on which semantic highlighting is performed.
  * @param presentationHighlighter Responsible of updating the editor's text presentation.
  * @param preferences             Semantic Highlighting user's preferences.
  * @param uiThread                Allows to run code in the UI Thread.
  */
class Presenter(
  editor: InteractiveCompilationUnitEditor,
  presentationHighlighter: TextPresentationHighlighter,
  preferences: Preferences,
  uiThread: UIThread) extends HasLogger { self =>
  import Presenter._

  private val job = {
    val job = new SemanticHighlightingJob(editor)
    job.setSystem(true)
    job.setPriority(Job.DECORATE)
    job
  }

  /** Keep tracks of all semantically highlighted positions in the editor.*/
  private val positionsTracker = new PositionsTracker

  private val documentSwapListener = new DocumentSwapListener(self, job)
  private val documentContentListener = new DocumentContentListener(job)
  private val positionUpdater = new PositionUpdater(positionsTracker)

  /** Should be called right after creating an instance of `this` class.
    *
    * @note Must be called from within the UI Thread
    *
    * @param forceRefresh Force a semantic reconciler run during initialization.
    */
  def initialize(forceRefresh: Boolean): Unit = {
    presentationHighlighter.initialize(job, positionsTracker)
    Option(presentationHighlighter.sourceViewer) foreach { sv =>
      sv.addTextInputListener(documentSwapListener)
      manageDocument(sv.getDocument)
    }
    if (forceRefresh) refresh()
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

  /** Stop the ongoing semantic reconciling job and unregister all editor/document's listeners.
    *
    * @note Must be called from within the UI Thread
    *
    * @param removesHighlights Force removal of all semantic highlighting styles from the editor.
    */
  def dispose(removesHighlights: Boolean): Unit = {
    job.cancel()
    /* invalidate the text presentation before disposing `presentationHighlighter`
     * (because `presentationHighlighter` contains the logic for applying the styles). */
    if (removesHighlights) removesAllHighlightings()
    presentationHighlighter.dispose()
    Option(presentationHighlighter.sourceViewer) foreach { sv => releaseDocument(sv.getDocument) }
  }

  /** Asynchronously refresh all semantic highlighting styles in the editor. */
  private def refresh(): Unit = { job.schedule() }

  /** Removes all highlighting styles from the editor.
    *
    * @note Must be called from within the UI Thread
    */
  private def removesAllHighlightings(): Unit = {
    positionsTracker.reset()
    Option(presentationHighlighter.sourceViewer) foreach (_.invalidateTextPresentation())
  }

  /** A background job that performs semantic highlighting.
    *
    * @note This class is thread-safe.
    */
  private class SemanticHighlightingJob(editor: InteractiveCompilationUnitEditor) extends Job("semantic highlighting") with HasLogger {

    override def run(monitor: IProgressMonitor): IStatus = {
      if (monitor.isCanceled()) Status.CANCEL_STATUS
      else performSemanticHighlighting(monitor)
    }

    private def performSemanticHighlighting(monitor: IProgressMonitor): IStatus = {
      editor.getInteractiveCompilationUnit.withSourceFile { (sourceFile, compiler) =>
        logger.debug("performing semantic highlighting on " + sourceFile.file.name)
        positionsTracker.startComputingNewPositions()
        val symbolInfos =
          try new SymbolClassification(sourceFile, compiler, preferences.isUseSyntacticHintsEnabled()).classifySymbols(monitor)
          catch {
            case e: Exception =>
              logger.error("Error while performing semantic highlighting", e)
              Nil
          }
        val newPositions = Position.from(symbolInfos)
        val positionsChange = positionsTracker.createPositionsChange(newPositions)
        val damagedRegion = positionsChange.affectedRegion()

        if (damagedRegion.getLength > 0) {
          val sortedPositions = newPositions.sorted.toArray
          /* if the positions held by the `positionsTracker` have changed, then
           * it's useless to proceed because the `newPositions` have computed on a
           * not up-to-date compilation unit. Let the next reconciler run take care
           * of re-computing the correct positions with the up-to-date content.
           */
          if (!positionsTracker.isDirty) {
            runPositionsUpdateInUiThread(sortedPositions, damagedRegion)
            Job.ASYNC_FINISH
          } else Status.OK_STATUS
        }
        else Status.OK_STATUS
      } getOrElse (Status.OK_STATUS)
    }

    private def runPositionsUpdateInUiThread(newPositions: Array[Position], damagedRegion: IRegion): Unit =
      uiThread.asyncExec {
        try {
          setThread(uiThread.get)
          if (!positionsTracker.isDirty) {
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
  class DocumentSwapListener(presenter: Presenter, semanticHighlightingJob: Job) extends ITextInputListener with HasLogger {
    override def inputDocumentAboutToBeChanged(oldInput: IDocument, newInput: IDocument): Unit = {
      semanticHighlightingJob.cancel()
      /* deletes all highlighted positions to avoid wrong colorings in the about to be displayed `newInput` document (the wrong
       * colors would be displayed only until the semantic reconciler has a chance to reconcile the swapped compilation unit.
       * Though, it makes sense to avoid the colors flickering and that's why `positionsTracker`'s state is reset.)
       */
      presenter.positionsTracker.reset()
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

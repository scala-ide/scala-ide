package scala.tools.eclipse.semantichighlighting.ui

import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.TextPresentationHighlighter
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import scala.tools.eclipse.util.SWTUtils
import scala.tools.eclipse.util.withDocument

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.jface.text.BadPositionCategoryException
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextPresentationListener
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.TextPresentation
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.widgets.Display

/** This class is responsible of:
  * - Triggering the semantic highlighting reconciler job as soon as the `JavaReconciler` has reconciled the compilation unit.
  * - Updating the editor's `TextPresentation` with the up-to-date highlighted positions.
  *
  * This class is thread-safe.
  */
private class TextPresentationEditorHighlighter(editor: ScalaSourceFileEditor) extends TextPresentationHighlighter with InteractiveCompilationUnitEditor {

  private class PerformSemanticHighlightingOnReconcilation extends IJavaReconcilingListener {
    override def aboutToBeReconciled(): Unit = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit = {
      // If the currently running reconciliation has been cancelled, then don't do anything (if a previous semantic highlighting reconciler job is 
      // still running, let it terminate. This will cause the editor to be updated with more up-to-date semantic highlights. 
      // TBH, I don't think anything bad would happen if we always first cancel the (possibly) on-going semantic highlighting reconciler job, but 
      // at the moment I feel there is no need for doing so.  
      if (!progressMonitor.isCanceled()) {
        reconciler.cancel()
        reconciler.schedule()
      }
    }
  }

  private val highlightingOnReconciliationListener = new PerformSemanticHighlightingOnReconcilation

  @volatile private var reconciler: Job = _
  @volatile private var textPresentationChangeListener: ITextPresentationListener = _

  override def sourceViewer: JavaSourceViewer = editor.sourceViewer
  override def getInteractiveCompilationUnit: InteractiveCompilationUnit = editor.getInteractiveCompilationUnit

  override def initialize(_reconciler: Job, _positionCategory: String): Unit = {
    reconciler = _reconciler
    editor.addReconcilingListener(highlightingOnReconciliationListener)
    textPresentationChangeListener = new TextPresentationEditorHighlighter.ApplyHighlightingTextPresentationChanges(sourceViewer, _positionCategory)
    sourceViewer.prependTextPresentationListener(textPresentationChangeListener)
  }

  override def dispose(): Unit = {
    if (editor != null) editor.removeReconcilingListener(highlightingOnReconciliationListener)
    if (sourceViewer != null) sourceViewer.removeTextPresentationListener(textPresentationChangeListener)
  }

  /** @inheritdoc
    * This method expects the caller thread to be the one associated with the `reconciler` job.
    *
    * @return Always return Job.ASYNC_FINISH, because the `TextPresentation`'s update has to be
    *  executed in the UI Thread asynchronously (it's done asynchronously because, as a
    *  general rule, we never block the UI if we can do otherwise).
    */
  override def updateTextPresentation(damage: IRegion): IStatus = {
    if (damage.getLength > 0) {
      reconciler.setThread(Thread.currentThread())
      SWTUtils.asyncExec {
        reconciler.setThread(Display.getDefault.getThread())
        val textPresentation = createRepairDescription(damage)
        textPresentation match {
          case None     => sourceViewer.invalidateTextPresentation() // invalidate the whole editor's text presentation
          case Some(tp) => sourceViewer.changeTextPresentation(tp, /*controlRedraw=*/ false)
        }
        reconciler.done(Status.OK_STATUS)
      }
      Job.ASYNC_FINISH
    }
    else Status.OK_STATUS
  }

  private def createRepairDescription(damage: IRegion): Option[TextPresentation] = withDocument(sourceViewer) { document =>
    val configuration = editor.createJavaSourceViewerConfiguration()
    val presentationReconciler = configuration.getPresentationReconciler(sourceViewer)
    presentationReconciler.createRepairDescription(damage, document)
  }
}

object TextPresentationEditorHighlighter {

  def apply(editor: ScalaSourceFileEditor): TextPresentationHighlighter with InteractiveCompilationUnitEditor = new TextPresentationEditorHighlighter(editor)

  /** This class is responsible of side-effecting the editor's text presentation by applying the semantic highlighting styles for all positions registered
    * in the document for the passed `category`.
    *
    * @param sourceViewer Used to retrieve the document to which this listener is attached to. The document is needed to retrieve all the positions to semantically highlight.
    * @param category     The position's category in the document.
    */
  private class ApplyHighlightingTextPresentationChanges(sourceViewer: ISourceViewer, category: String) extends ITextPresentationListener with HasLogger {
    override def applyTextPresentation(textPresentation: TextPresentation): Unit = withDocument(sourceViewer) { document =>
      val highlightedPositions = {
        try document.getPositions(category)
        catch {
          case e: BadPositionCategoryException =>
            logger.error(e) // should never happen
            Array.empty[Position]
        }
      }

      val damagedRegion = textPresentation.getExtent()
      val offset = damagedRegion.getOffset
      val end = offset + damagedRegion.getLength

      // Creates the `StyleRange`'s only for the positions that are included in the `damagedRegion`. The `damagedRegion` is the portion of the editor whose  
      // styles needs to be recomputed. FOr all the other positions, we rely on the `DocumentProxy$HighlightedPositionUpdater` listener, whose purpose is to 
      // shift positions' offset as needed when the document is edited.
      val styles = for {
        pos <- highlightedPositions
        val posOffset = pos.getOffset
        if (posOffset >= offset) && (posOffset <= end) && !pos.isDeleted()
      } yield pos.asInstanceOf[HighlightedPosition].createStyleRange

      textPresentation.replaceStyleRanges(styles.toArray)
    }
  }
}
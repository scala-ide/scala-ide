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
import scala.tools.eclipse.semantichighlighting.DocumentPositionsChange

/** This class is responsible of:
  * - Triggering the semantic highlighting reconciler job as soon as the `JavaReconciler` has reconciled the compilation unit.
  * - Updating the editor's `TextPresentation` with the up-to-date highlighted positions.
  *
  * @note This class is thread-safe.
  */
private class TextPresentationEditorHighlighter(editor: ScalaSourceFileEditor) extends TextPresentationHighlighter {

  private class PerformSemanticHighlightingOnReconcilation extends IJavaReconcilingListener {
    override def aboutToBeReconciled(): Unit = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit = {
      // There is no need to call `reconciler.cancel()` here because in the document we register a listener that 
      // already does it whenever the document is about to be changed. And `this` reconciling listener always gets 
      // executed *after* the aforementioned document listener (check `Presenter$DefaultDocumentListener` for 
      // more details).
      // Furthermore, a new semantic highlighting reconciler run is only scheduled if the current reconciling was 
      // not cancelled. If it was cancelled, it usually means that the editor was closed, or the document was change,
      // and hence the compilation unit will be soon reconciled again. Therefore, there is no need to waste any cycle.
      if (!progressMonitor.isCanceled()) reconciler.schedule()
    }
  }

  private val highlightingOnReconciliationListener = new PerformSemanticHighlightingOnReconcilation

  @volatile private var reconciler: Job = _
  @volatile private var textPresentationChangeListener: ITextPresentationListener = _

  override def sourceViewer: JavaSourceViewer = editor.sourceViewer

  override def initialize(_reconciler: Job, _positionCategory: String): Unit = {
    reconciler = _reconciler
    if(editor != null) editor.addReconcilingListener(highlightingOnReconciliationListener)
    textPresentationChangeListener = new TextPresentationEditorHighlighter.ApplyHighlightingTextPresentationChanges(sourceViewer, _positionCategory)
    if(sourceViewer != null) sourceViewer.prependTextPresentationListener(textPresentationChangeListener)
  }

  override def dispose(): Unit = {
    if (editor != null) editor.removeReconcilingListener(highlightingOnReconciliationListener)
    if (sourceViewer != null) sourceViewer.removeTextPresentationListener(textPresentationChangeListener)
  }

  /** @inheritdoc */
  override def updateTextPresentation(positionsChange: DocumentPositionsChange): Unit = {
    val damage = positionsChange.createRegionChange()
    if (damage.getLength > 0) {
      val textPresentation = createRepairDescription(damage)
      SWTUtils.asyncExec {
        textPresentation match {
          // Both will trigger the `TextPresentationEditorHighlighter$ApplyHighlightingTextPresentationChanges.applyTextPresentation` method to be called.
          case None     => sourceViewer.invalidateTextPresentation() // invalidate the whole editor's text presentation
          case Some(tp) => sourceViewer.changeTextPresentation(tp, /*controlRedraw=*/ false)
        }
      }
    }
  }

  private def createRepairDescription(damage: IRegion): Option[TextPresentation] = withDocument(sourceViewer) { document =>
    val configuration = editor.createJavaSourceViewerConfiguration()
    val presentationReconciler = configuration.getPresentationReconciler(sourceViewer)
    presentationReconciler.createRepairDescription(damage, document)
  }
}

object TextPresentationEditorHighlighter {

  def apply(editor: ScalaSourceFileEditor): TextPresentationHighlighter = new TextPresentationEditorHighlighter(editor)

  /** This class is responsible of side-effecting the editor's text presentation by applying the semantic highlighting styles for all positions registered
    * in the document for the passed `category`.
    *
    * @note This listener should always be called within the UI Thread.
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
      val name = org.eclipse.swt.widgets.Display.getDefault().getThread().getName()
      val damagedRegion = textPresentation.getExtent()
      val offset = damagedRegion.getOffset
      val end = offset + damagedRegion.getLength

      // Creates the `StyleRange`'s only for the positions that are included in the `damagedRegion`. The `damagedRegion` is the portion of the editor whose  
      // styles needs to be recomputed. For all the other positions, we rely on the `DocumentProxy$HighlightedPositionUpdater` listener, whose purpose is to 
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
package scala.tools.eclipse.semantichighlighting.ui

import scala.collection.immutable
import scala.tools.eclipse.ScalaCompilationUnitEditor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.PositionsTracker
import scala.tools.eclipse.semantichighlighting.Preferences
import scala.tools.eclipse.semantichighlighting.TextPresentationHighlighter
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import scala.tools.eclipse.util.withDocument

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.text.java.IJavaReconcilingListener
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextPresentationListener
import org.eclipse.jface.text.TextPresentation
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.custom.StyleRange

/** This class is responsible of:
 *
  * - Triggering the semantic highlighting job as soon as the [[org.eclipse.jdt.internal.ui.text.JavaReconciler]]
  * has finished reconciling the opened compilation unit.
  *
  * - Updating the editor's text presentation with the up-to-date semantic highlighting styles.
  *
  * @note All accesses to this class are confined to the UI Thread.
  */
private class TextPresentationEditorHighlighter(editor: ScalaCompilationUnitEditor, preferences: Preferences, addReconcilingListener: IJavaReconcilingListener => Unit, removeReconcilingListener: IJavaReconcilingListener => Unit) extends TextPresentationHighlighter {
  import TextPresentationEditorHighlighter._

  @volatile private var highlightingOnReconciliation: IJavaReconcilingListener = _
  @volatile private var textPresentationChangeListener: ApplyHighlightingTextPresentationChanges = _

  override def initialize(semanticHighlightingJob: Job, positionsTracker: PositionsTracker): Unit = {
    highlightingOnReconciliation = new PerformSemanticHighlightingOnReconcilation(semanticHighlightingJob)
    textPresentationChangeListener = new ApplyHighlightingTextPresentationChanges(semanticHighlightingJob, positionsTracker, preferences)

    Option(preferences.store) foreach (_.addPropertyChangeListener(textPresentationChangeListener))
    addReconcilingListener(highlightingOnReconciliation)
    // it's important to prepend the listener or semantic highlighting coloring will hide the style applied for hyperlinking when the
    // user hovers on a semantically highlighted binding.
    Option(sourceViewer) foreach (_.prependTextPresentationListener(textPresentationChangeListener))
  }

  override def dispose(): Unit = {
    Option(preferences.store) foreach (_.removePropertyChangeListener(textPresentationChangeListener))
    removeReconcilingListener(highlightingOnReconciliation)
    Option(sourceViewer) foreach (_.removeTextPresentationListener(textPresentationChangeListener))
  }

  override def sourceViewer: JavaSourceViewer = editor.sourceViewer

  override def updateTextPresentation(damage: IRegion): Unit = {
    val textPresentation = createRepairDescription(damage)
    textPresentation match {
      case None     => sourceViewer.invalidateTextPresentation() // invalidate the whole editor's text presentation
      case Some(tp) => sourceViewer.changeTextPresentation(tp, /*controlRedraw=*/ false)
    }
  }

  private def createRepairDescription(damage: IRegion): Option[TextPresentation] = withDocument(sourceViewer) { document =>
    val configuration = editor.createJavaSourceViewerConfiguration()
    val presentationReconciler = configuration.getPresentationReconciler(sourceViewer)
    presentationReconciler.createRepairDescription(damage, document)
  }
}

object TextPresentationEditorHighlighter {

  def apply(editor: ScalaCompilationUnitEditor, preferences: Preferences, addReconcilingListener: IJavaReconcilingListener => Unit, removeReconcilingListener: IJavaReconcilingListener => Unit): TextPresentationHighlighter =
    new TextPresentationEditorHighlighter(editor, preferences, addReconcilingListener, removeReconcilingListener)

  private class PerformSemanticHighlightingOnReconcilation(semanticHighlightingJob: Job) extends IJavaReconcilingListener {
    override def aboutToBeReconciled(): Unit = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit = {
      /* There is no need to call `semanticHighlightingJob.cancel()` here because the document has a listener that
       * already cancels the ongoing semantic highlighting job whenever the document is about to be changed. And `this`
       * reconciling listener always gets executed '''after''' the aforementioned listener (check
       * [[scala.tools.eclipse.semantichighlighting.Presenter$DocumentContentListener]] for more details).
       *
       * Furthermore, a new semantic highlighting job run is only scheduled if the ongoing reconciliation has not been
       * cancelled. If it was cancelled, this usually means that the editor was closed, or the document was change.
       * In the editor was closed, there is clearly no need for reconciling. While, if the document changed, then the
       * compilation unit will be soon reconciled again.
       */
      if (!progressMonitor.isCanceled()) semanticHighlightingJob.schedule()
    }
  }

  /** This class is responsible of applying the semantic highlighting styles in the editor.
    *
    * @note Mind that the implementation needs to be blazing fast because `applyTextPresentation` is called at '''every'''
    * keystroke (and, often, more than once). If it takes more than a few milliseconds to execute, users will perceive
    * the slow-down when typing.
    *
    * @param positionsTracker Holds the semantic positions that needs to be colored in the editor.
    * @param preferences      The user's preferences.
    */
  private class ApplyHighlightingTextPresentationChanges(reconciler: Job, positionsTracker: PositionsTracker, preferences: Preferences) extends IPropertyChangeListener with ITextPresentationListener with HasLogger {

    private var semanticCategory2style: immutable.Map[SymbolTypes.SymbolType, HighlightingStyle] = {
      (for (symType <- SymbolTypes.values) yield (symType -> HighlightingStyle(preferences, symType)))(collection.breakOut)
    }

    override def propertyChange(event: PropertyChangeEvent): Unit = {
      if (event.getProperty().startsWith(ScalaSyntaxClasses.IDENTIFIER_IN_INTERPOLATED_STRING.baseName + ".")) {
        val syms: Set[SymbolTypes.SymbolType] = positionsTracker.identifiersInInterpolatedStrings.map(_.kind)(collection.breakOut)
        invalidateSymTypes(syms.toSeq: _*)
      } else {
        for {
          semanticCategory <- ScalaSyntaxClasses.scalaSemanticCategory.children
          if event.getProperty().startsWith(semanticCategory.baseName)
          symType: SymbolTypes.SymbolType <- SymbolTypes.values.find(HighlightingStyle.symbolTypeToSyntaxClass(_) == semanticCategory)
        } invalidateSymTypes(symType)
      }
    }

    private def invalidateSymTypes(symTypes: SymbolTypes.SymbolType*) {
      for (symType <- symTypes) {
        semanticCategory2style += symType -> HighlightingStyle(preferences, symType)
        positionsTracker.deletesPositionsOfType(symType)
      }
      reconciler.schedule()
    }

    override def applyTextPresentation(textPresentation: TextPresentation): Unit = {
      val damagedRegion = textPresentation.getExtent() // Portion of the editor whose styles needs to be recomputed.

      val positions = positionsTracker.positionsInRegion(damagedRegion)
      val styles: Array[StyleRange] = {
        for {
          position <- positions
          style = semanticCategory2style(position.kind)
          if (style.enabled || position.shouldStyle) && !position.isDeleted()
        } yield style.style(position)
      }

      textPresentation.replaceStyleRanges(styles)
    }
  }
}
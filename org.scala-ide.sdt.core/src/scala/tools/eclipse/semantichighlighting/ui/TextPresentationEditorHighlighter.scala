package scala.tools.eclipse.semantichighlighting.ui

import scala.collection.immutable
import scala.tools.eclipse.ScalaSourceFileEditor
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
  * - Triggering the semantic highlighting reconciler job as soon as the [[org.eclipse.jdt.internal.ui.text.JavaReconciler]]
  * has finished reconciling the compilation unit opened in the `editor`.
  * - Updating the editor's presentation with the up-to-date styles.
  *
  * @note This class assumes to be accessed always within the UI Thread.
  */
private class TextPresentationEditorHighlighter(editor: ScalaSourceFileEditor, preferences: Preferences) extends TextPresentationHighlighter {
  import TextPresentationEditorHighlighter._

  @volatile private var highlightingOnReconciliationListener: IJavaReconcilingListener = _
  @volatile private var textPresentationChangeListener: ApplyHighlightingTextPresentationChanges = _

  override def initialize(reconciler: Job, positionsTracker: PositionsTracker): Unit = {
    highlightingOnReconciliationListener = new PerformSemanticHighlightingOnReconcilation(reconciler)
    textPresentationChangeListener = new ApplyHighlightingTextPresentationChanges(reconciler, positionsTracker, preferences)

    Option(preferences.store) foreach (_.addPropertyChangeListener(textPresentationChangeListener))
    Option(editor) foreach (_.addReconcilingListener(highlightingOnReconciliationListener))
    Option(sourceViewer) foreach (_.prependTextPresentationListener(textPresentationChangeListener))
  }

  override def dispose(): Unit = {
    Option(preferences.store) foreach (_.removePropertyChangeListener(textPresentationChangeListener))
    Option(editor) foreach (_.removeReconcilingListener(highlightingOnReconciliationListener))
    Option(sourceViewer) foreach (_.removeTextPresentationListener(textPresentationChangeListener))
  }

  override def sourceViewer: JavaSourceViewer = editor.sourceViewer

  /** @inheritdoc */
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

  def apply(editor: ScalaSourceFileEditor, preferences: Preferences): TextPresentationHighlighter = new TextPresentationEditorHighlighter(editor, preferences)

  private class PerformSemanticHighlightingOnReconcilation(reconciler: Job) extends IJavaReconcilingListener {
    override def aboutToBeReconciled(): Unit = ()
    override def reconciled(ast: CompilationUnit, forced: Boolean, progressMonitor: IProgressMonitor): Unit = {
      /* There is no need to call `reconciler.cancel()` here because in the document we register a listener that
       * already cancels the current reconciler run whenever the document is about to be changed. And `this` 
       * reconciling listener always gets executed '''after''' the aforementioned document listener (check 
       * [[scala.tools.eclipse.semantichighlighting.Presenter$DocumentContentListener]] for more details).
       * 
       * Furthermore, a new semantic highlighting reconciler run is only scheduled if the current reconciling was
       * not cancelled. If it was cancelled, this usually means that the editor was closed, or the document was change.
       * In the editor was closed, there is clearly no need for reconciling. While, if the document changed, then the 
       * compilation unit will be soon reconciled again.
       */
      if (!progressMonitor.isCanceled()) reconciler.schedule()
    }
  }

  /** This class is responsible of applying the semantic highlighting styles in the editor.
    *
    * @note Mind that the implementation needs to be blazing fast because `applyTextPresentation` is called at '''every'''
    * keystroke (and, often, more than once). If it takes more than a few milliseconds to execute, users will perceive
    * the slowdown when typing.
    *
    * @param positionsTracker Holds the semantic positions that needs to be colored in the editor.
    * @param preferences      The user's preferences.
    */
  private class ApplyHighlightingTextPresentationChanges(reconciler: Job, positionsTracker: PositionsTracker, preferences: Preferences) extends IPropertyChangeListener with ITextPresentationListener with HasLogger {

    private var semanticCategory2style: immutable.Map[SymbolTypes.SymbolType, HighlightingStyle] = {
      (for (symType <- SymbolTypes.values) yield (symType -> HighlightingStyle(preferences, symType)))(collection.breakOut)
    }

    override def propertyChange(event: PropertyChangeEvent): Unit = {
      for {
        semanticCategory <- ScalaSyntaxClasses.scalaSemanticCategory.children
        if event.getProperty().startsWith(semanticCategory.baseName)
        symType: SymbolTypes.SymbolType <- SymbolTypes.values.find(HighlightingStyle.symbolTypeToSyntaxClass(_) == semanticCategory)
      } {
        semanticCategory2style += symType -> HighlightingStyle(preferences, symType)
        positionsTracker.deletesPositionsOfType(symType)
        reconciler.schedule()
      }
    }

    override def applyTextPresentation(textPresentation: TextPresentation): Unit = {
      val damagedRegion = textPresentation.getExtent() // Portion of the editor whose styles needs to be recomputed.

      val positions = positionsTracker.positionsInRegion(damagedRegion)
      val styles: Array[StyleRange] = {
        (for {
          pos <- positions
          style = semanticCategory2style(pos.kind)
          if style.enabled
        } yield style.style(pos))(collection.breakOut)
      }

      textPresentation.replaceStyleRanges(styles)
    }
  }
}
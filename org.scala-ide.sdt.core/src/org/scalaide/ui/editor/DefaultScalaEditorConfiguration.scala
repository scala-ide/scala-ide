package org.scalaide.ui.editor

import org.eclipse.jface.text.source.IAnnotationHover
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.reconciler.MonoReconciler
import org.eclipse.jface.text.source.DefaultAnnotationHover
import org.scalaide.core.lexical.ScalaPartitions
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.scalaide.core.lexical.ScalaCodeScanners
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.jface.text.information.InformationPresenter
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IDocument
import org.scalaide.ui.editor.hover.IScalaHover
import org.eclipse.jface.text.ITextHover
import org.scalaide.core.IScalaPlugin
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.text.rules.DefaultDamagerRepairer
import org.eclipse.ui.texteditor.ChainedPreferenceStore

/** A default SourceViewerConfiguration class for Scala-based editors.
 *
 *  This class can be subclassed. It provides a default configuration including
 *  reconciliation (errors-as-you-type), hovers and Scala syntax highlighting.
 *
 *  The workhorse of this implementation is [[InteractiveCompilationUnit]], which provides
 *  the mapping between original source and Scala-source.
 *
 *  Any method can be overwritten.
 *
 *  @note Standard content assist is missing from this configuration, but should be easy to add
 *        in a subsequent release
 *
 *  @since 4.3.0
 */
trait DefaultScalaEditorConfiguration extends SourceViewerConfiguration {
  val javaPreferenceStore: IPreferenceStore
  val textEditor: InteractiveCompilationUnitEditor

  protected def scalaPreferenceStore: IPreferenceStore = IScalaPlugin().getPreferenceStore
  private val combinedPrefStore = new ChainedPreferenceStore(
    Array(scalaPreferenceStore, javaPreferenceStore))
  private val codeHighlightingScanners = ScalaCodeScanners.codeHighlightingScanners(combinedPrefStore)

  override def getPresentationReconciler(sv: ISourceViewer) = {
    val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]

    for ((partitionType, tokenScanner) <- codeHighlightingScanners) {
      val dr = new DefaultDamagerRepairer(tokenScanner)
      reconciler.setDamager(dr, partitionType)
      reconciler.setRepairer(dr, partitionType)
    }

    reconciler
  }

  override def getReconciler(sourceViewer: ISourceViewer): IReconciler = {
    val reconciler = new MonoReconciler(
      new ReconcilingStrategy(textEditor, reloader), /*isIncremental = */ false)
    reconciler.install(sourceViewer)
    reconciler
  }

  /** Ask the underlying unit to be scheduled for the next reconciliation round */
  private object reloader extends IDocumentListener {
    override def documentChanged(event: DocumentEvent) = {
      textEditor.getInteractiveCompilationUnit().scheduleReconcile(event.getDocument.get.toCharArray)
    }

    override def documentAboutToBeChanged(event: DocumentEvent) = {}
  }

  override def getTextHover(viewer: ISourceViewer, contentType: String): ITextHover = {
    IScalaHover(textEditor)
  }

  override def getConfiguredContentTypes(sourceViewer: ISourceViewer): Array[String] = {
    Array(IDocument.DEFAULT_CONTENT_TYPE,
      IJavaPartitions.JAVA_DOC,
      IJavaPartitions.JAVA_MULTI_LINE_COMMENT,
      IJavaPartitions.JAVA_SINGLE_LINE_COMMENT,
      IJavaPartitions.JAVA_STRING,
      IJavaPartitions.JAVA_CHARACTER,
      ScalaPartitions.SCALA_MULTI_LINE_STRING)
  }

  override def getAnnotationHover(viewer: ISourceViewer): IAnnotationHover = {
    new DefaultAnnotationHover(true)
  }

  override def getInformationPresenter(sourceViewer: ISourceViewer) = {
    val p = new InformationPresenter(getInformationControlCreator(sourceViewer))
    val ip = IScalaHover.hoverInformationProvider(textEditor)

    p.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer))
    getConfiguredContentTypes(sourceViewer) foreach (p.setInformationProvider(ip, _))
    p
  }

  def propertyChange(event: PropertyChangeEvent): Unit = {
    codeHighlightingScanners.values.foreach(_.adaptToPreferenceChange(event))
  }
}

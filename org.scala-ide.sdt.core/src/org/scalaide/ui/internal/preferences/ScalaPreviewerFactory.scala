package org.scalaide.ui.internal.preferences

import org.eclipse.jface.text.source.SourceViewer
import org.eclipse.ui.editors.text.EditorsUI
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Document
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.swt.SWT
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.swt.widgets.Control
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.TextUtilities
import java.util.HashMap
import org.eclipse.swt.widgets.Composite
import org.scalaide.ui.internal.editor.ScalaSourceViewerConfiguration
import org.scalaide.util.internal.ui.DisplayThread

object ScalaPreviewerFactory {

  def createPreviewer(parent: Composite, scalaPreferenceStore: IPreferenceStore, initialText: String): SourceViewer = {
    val preferenceStore = new ChainedPreferenceStore(Array(scalaPreferenceStore, EditorsUI.getPreferenceStore))
    val previewViewer = new JavaSourceViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER, preferenceStore)
    val font = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    previewViewer.getTextWidget.setFont(font)
    previewViewer.setEditable(false)

    val configuration = new ScalaSourceViewerConfiguration(preferenceStore, preferenceStore, null)
    previewViewer.configure(configuration)

    val document = new Document
    document.set(initialText)
    val partitioners = new HashMap[String, IDocumentPartitioner]
    partitioners.put(IJavaPartitions.JAVA_PARTITIONING, new ScalaDocumentPartitioner(conservative = true))
    TextUtilities.addDocumentPartitioners(document, partitioners)
    previewViewer.setDocument(document)

    preferenceStore.addPropertyChangeListener(new IPropertyChangeListener {
      def propertyChange(event: PropertyChangeEvent) {
        if (configuration.affectsTextPresentation(event))
          configuration.handlePropertyChangeEvent(event)
        DisplayThread.asyncExec(previewViewer.invalidateTextPresentation())
      }
    })
    previewViewer
  }

}

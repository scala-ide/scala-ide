package org.scalaide.ui.internal.preferences

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocumentPartitioner
import org.scalaide.ui.internal.editor.ScalaSourceViewerConfiguration
import org.scalaide.ui.syntax.preferences.PreviewerFactoryConfiguration
import org.scalaide.core.lexical.ScalaCodePartitioner
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.ui.syntax.ScalaSyntaxClasses
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.HighlightingStyle
import org.scalaide.ui.internal.editor.decorators.semantichighlighting.Preferences
import org.eclipse.jface.util.PropertyChangeEvent

class StandardPreviewerFactoryConfiguration extends PreviewerFactoryConfiguration {

  def getConfiguration(preferenceStore: org.eclipse.jface.preference.IPreferenceStore): PreviewerFactoryConfiguration.PreviewerConfiguration = {
    new ScalaSourceViewerConfiguration(preferenceStore, preferenceStore, null)
  }

  def getDocumentPartitioners(): Map[String, IDocumentPartitioner] =
    Map((IJavaPartitions.JAVA_PARTITIONING, ScalaCodePartitioner.documentPartitioner(conservative = true)))
}

object ScalaPreviewerFactoryConfiguration extends StandardPreviewerFactoryConfiguration

object SemanticPreviewerFactoryConfiguration extends StandardPreviewerFactoryConfiguration {
  override def additionalStyling(viewer: ISourceViewer, store: IPreferenceStore) {
    val textWidgetOpt = Option(viewer.getTextWidget)
    for {
      textWidget <- textWidgetOpt
      position <- SyntaxColoringPreferencePage.semanticLocations
    } if (store.getBoolean(ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING)
      && (store.getBoolean(HighlightingStyle.symbolTypeToSyntaxClass(position.kind).enabledKey)
        || position.shouldStyle)) {
      val styleRange = HighlightingStyle(Preferences(store), position.kind).style(position)
      textWidget.setStyleRange(styleRange)
    }
  }
}
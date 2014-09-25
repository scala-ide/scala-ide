package org.scalaide.ui.internal.preferences

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocumentPartitioner
import org.scalaide.ui.internal.editor.ScalaSourceViewerConfiguration
import org.scalaide.ui.syntax.preferences.PreviewerFactoryConfiguration
import org.scalaide.core.lexical.ScalaCodePartitioner

object ScalaPreviewerFactoryConfiguration extends PreviewerFactoryConfiguration {

  def getConfiguration(preferenceStore: org.eclipse.jface.preference.IPreferenceStore): PreviewerFactoryConfiguration.PreviewerConfiguration = {
    new ScalaSourceViewerConfiguration(preferenceStore, preferenceStore, null)
  }

  def getDocumentPartitioners(): Map[String, IDocumentPartitioner] =
    Map((IJavaPartitions.JAVA_PARTITIONING, ScalaCodePartitioner.documentPartitioner(conservative = true)))

}

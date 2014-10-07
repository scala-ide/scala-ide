package org.scalaide.ui.syntax.preferences

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.jface.text.IDocumentPartitioner

/** Configuration for a previewer, like the one used in syntax coloring preferences page.
 *  It returns the configuration for the source viewer, and the document partitioners to use.
 */
trait PreviewerFactoryConfiguration {

  import PreviewerFactoryConfiguration._

  /** Returns the configuration to use on the source viewer.
   *  The configuration needs to also be an [[IPropertyChangeListener]], and react to preferences
   *  changes.
   *
   *  @param preferenceStore the preference store to use for the configuration.
   */
  def getConfiguration(preferenceStore: IPreferenceStore): PreviewerConfiguration

  /** Returns the document partitioniers to use in the previewer.
   */
  def getDocumentPartitioners(): Map[String, IDocumentPartitioner]

}

object PreviewerFactoryConfiguration {
  type PreviewerConfiguration = SourceViewerConfiguration with IPropertyChangeListener
}
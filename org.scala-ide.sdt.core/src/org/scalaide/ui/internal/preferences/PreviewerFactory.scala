package org.scalaide.ui.internal.preferences

import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.jface.text.source.SourceViewer
import org.eclipse.jface.text.source.projection.ProjectionViewer
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.editors.text.EditorsUI
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.eclipse.jface.text.TextUtilities
import java.util.HashMap
import org.scalaide.ui.syntax.preferences.PreviewerFactoryConfiguration

/** Factory for previewers used in syntax coloring preference pages and other places. It takes source viewer configuration
 *  and document partitioners through the `factoringConfiguration` parameter.
 */
class PreviewerFactory(factoryConfiguration: PreviewerFactoryConfiguration) extends IPropertyChangeListener {

  private var chainedPreferenceStore: ChainedPreferenceStore = _
  private var previewViewer: ProjectionViewer = _
  private var configuration: PreviewerFactoryConfiguration.PreviewerConfiguration = _

  def createPreviewer(parent: Composite, preferenceStore: IPreferenceStore, initialText: String): SourceViewer = {
    chainedPreferenceStore = new ChainedPreferenceStore(Array(preferenceStore, EditorsUI.getPreferenceStore))
    previewViewer = new ProjectionViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER)
    configuration = factoryConfiguration.getConfiguration(chainedPreferenceStore)
    val font = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    previewViewer.getTextWidget.setFont(font)
    previewViewer.setEditable(false)
    previewViewer.configure(configuration)

    val document = new Document
    document.set(initialText)

    import scala.collection.JavaConverters._
    TextUtilities.addDocumentPartitioners(document, asJavaHashMap(factoryConfiguration.getDocumentPartitioners()))
    previewViewer.setDocument(document)

    chainedPreferenceStore.addPropertyChangeListener(this)
    factoryConfiguration.additionalStyling(previewViewer,  chainedPreferenceStore)
    previewViewer
  }

  /** Create a Java [HashMap] with the content of the given map.
   *  It is required for the call to [[TextUtilities#addDocumentPartitioners]], as the provided map
   *  is cleaned during the execution..
   *  The [[HashMap]] returned when using [[JavaConvertes#asJava]] does not support the clean operation.
   */
  private def asJavaHashMap(map: Map[String, IDocumentPartitioner]): HashMap[String, IDocumentPartitioner] = {
    val res = new HashMap[String, IDocumentPartitioner]
    map.foreach { entry =>
      res.put(entry._1, entry._2)
    }
    res
  }

  def disposePreviewer() {
    chainedPreferenceStore.removePropertyChangeListener(this)
  }

  def propertyChange(event: PropertyChangeEvent) {
    // tell configuration to take into account the changes as well
    configuration.propertyChange(event)
    // refreshes the highlighting
    previewViewer.invalidateTextPresentation()

    factoryConfiguration.additionalStyling(previewViewer, chainedPreferenceStore)
  }
}

package scala.tools.eclipse.semantichighlighting

import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.source.ISourceViewer

/** This interface expose the minimal amount of functionality needed by the semantic highlighting
 *  component to apply the presentation styles in a text editor.
 *
 *  @note This trait is needed for running tests in a headless environment.
 */
trait TextPresentationHighlighter {
  def sourceViewer: ISourceViewer

  def initialize(semanticHighlightingJob: Job, positionsTracker: PositionsTracker): Unit
  def dispose(): Unit

  /** Triggers an update of the editor's `TextPresentation` based on the passed `damage` region.*/
  def updateTextPresentation(damage: IRegion): Unit
}
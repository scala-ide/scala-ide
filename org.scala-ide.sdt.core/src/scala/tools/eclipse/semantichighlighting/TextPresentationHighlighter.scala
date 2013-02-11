package scala.tools.eclipse.semantichighlighting

import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.source.ISourceViewer

/** This interface expose the minimal amount of functionality needed by the semantic highlighting 
 *  component to apply the presentation styles in a text editor.
 */
trait TextPresentationHighlighter {
  def sourceViewer: ISourceViewer

  def initialize(reconciler: Job, positionsTracker: PositionsTracker): Unit
  def dispose(): Unit
  
  /** Triggers an update of the editor's `TextPresentation` based on the passed `damage` region.*/
  def updateTextPresentation(damage: IRegion): Unit
}
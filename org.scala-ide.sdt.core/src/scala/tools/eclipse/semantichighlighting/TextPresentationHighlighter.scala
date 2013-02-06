package scala.tools.eclipse.semantichighlighting

import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.source.ISourceViewer
import scala.tools.eclipse.InteractiveCompilationUnit
import org.eclipse.core.runtime.Status

/** This interface expose the minimal amount of functionality needed by the semantic highlighting 
 *  component to apply the presentation styles in a text editor. This is needed for isolating all 
 *  UI logic so that it can be easily tested in a headless environment.
 */
trait TextPresentationHighlighter {
  def sourceViewer: ISourceViewer

  def initialize(reconciler: Job, positionCategory: String): Unit
  def dispose(): Unit
  
  /** Triggers an update of the editor's `TextPresentation` based on the passed `damage` region.*/
  def updateTextPresentation(damage: IRegion): IStatus
}
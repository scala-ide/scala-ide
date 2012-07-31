package scala.tools.eclipse

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.ITextEditor


/** An Editor that can expose its source viewer. */
trait ISourceViewerEditor extends ITextEditor {

  def getViewer(): ISourceViewer
}
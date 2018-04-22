package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.graphics.GC
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.scalaide.logging.HasLogger
import org.scalaide.ui.editor.hover.IScalaHover
import org.eclipse.swt.graphics.Point

/**
 * Contains additional logic that should be added to instances of
 * [[org.eclipse.jface.internal.text.html.BrowserInformationControl]].
 */
trait BrowserControlAdditions extends BrowserInformationControl with HasLogger {

  private var control: Control = _

  override def createContent(parent: Composite): Unit = {
    super.createContent(parent)

    control = parent.getChildren().head
  }

  /** Take size hint of super implementation but add additional width. */
  override def computeSizeHint(): Point = {
    val gc = new GC(control)
    gc.setFont(JFaceResources.getFontRegistry().get(IScalaHover.HoverFontId))
    val averageCharWidth = gc.getFontMetrics().getAverageCharacterWidth()
    gc.dispose()

    val p = super.computeSizeHint()
    p.x += (averageCharWidth * 10).toInt
    p
  }
}

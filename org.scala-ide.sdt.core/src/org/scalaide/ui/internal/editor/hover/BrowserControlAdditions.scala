package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.swt.graphics.TextLayout
import org.eclipse.swt.widgets.Composite
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.ReflectAccess

/**
 * Contains additional logic that should be added to instances of
 * [[org.eclipse.jface.internal.text.html.BrowserInformationControl]].
 */
trait BrowserControlAdditions extends BrowserInformationControl with HasLogger {

  private var additionalWidth: Int = _

  override def createContent(parent: Composite) = {
    super.createContent(parent)

    /*
     * Computes an additonal width that we have to add to the width of the
     * shown hover. This is necessary because some fonts and all CSS attributes
     * are not correctly considered by the super implementation of
     * [[computeSizeHint]].
     */
    val width = ReflectAccess[BrowserInformationControl](this) apply { ra =>
      val tl = ra.fTextLayout[TextLayout]
      // chooses an arbitrary character and get its width
      tl.setText("W")
      val width = tl.getBounds().width
      tl.setText("")
      width
    }

    width match {
      case util.Success(width) =>
        additionalWidth = width
      case util.Failure(f) =>
        logger.warn("An error occurred while trying to compute additional width for BrowserInformationControl", f)
    }
  }

  /** Take size hint of super implementation but add additional width. */
  override def computeSizeHint() = {
    val p = super.computeSizeHint()
    p.x += additionalWidth
    p
  }
}

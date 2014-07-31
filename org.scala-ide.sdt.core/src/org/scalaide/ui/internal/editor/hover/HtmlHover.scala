package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.resource.JFaceResources

/**
 * Contains convenience functions for HTML based hovers.
 */
trait HtmlHover {
  import ScalaHover._

  def createHtmlOutput(f: StringBuffer => Unit): String = {
    val b = new StringBuffer()

    val fd = JFaceResources.getFontRegistry().getFontData(HoverFontId)(0)
    val css = HTMLPrinter.convertTopLevelFont(ScalaHoverStyeSheet, fd)
    HTMLPrinter.insertPageProlog(b, 0, css)

    f(b)

    HTMLPrinter.addPageEpilog(b)
    b.toString()
  }
}

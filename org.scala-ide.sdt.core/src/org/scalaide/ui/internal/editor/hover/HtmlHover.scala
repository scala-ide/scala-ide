package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.resource.JFaceResources

/**
 * Contains convenience functions for HTML based hovers.
 */
trait HtmlHover {
  import ScalaHover._
  import HTMLPrinter._

  def createHtmlOutput(f: StringBuffer => Unit): String = {
    val b = new StringBuffer()

    val fd = JFaceResources.getFontRegistry().getFontData(HoverFontId)(0)
    val css = convertTopLevelFont(ScalaHoverStyleSheet, fd)
    insertPageProlog(b, 0, css)

    f(b)

    addPageEpilog(b)
    b.toString()
  }

  /**
   * Converts content with a markdown syntax to HTML. The following features are
   * supported:
   *
   * - code blocks (surrounded by \`\`)
   * - ASCII to UTF conversion (=> to ⇒ for example)
   */
  def convertContentToHtml(content: String): String = {
    convertToHTMLContent(content)
      .replaceAll("""`([\S\s]*?)`""", "<code>$1</code>")
      .replaceAll("=&gt;", "⇒")
      .replaceAll("-&gt;", "→")
  }
}

package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.resource.JFaceResources
import org.scalaide.ui.editor.hover.IScalaHover

/**
 * Contains convenience functions for HTML based hovers.
 */
trait HtmlHover {
  import IScalaHover._
  import HTMLPrinter._

  def createHtmlOutput(f: java.lang.StringBuilder => Unit): String = {
    val b = new java.lang.StringBuilder()

    val fd = JFaceResources.getFontRegistry().getFontData(HoverFontId)(0)
    val configuredFont = convertTopLevelFont("""html { font-family: sans-serif; font-size: 10pt; font-style: normal; font-weight: normal; }""", fd)
    insertPageProlog(b, 0, s"$configuredFont\n\n$ScalaHoverStyleSheet")

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

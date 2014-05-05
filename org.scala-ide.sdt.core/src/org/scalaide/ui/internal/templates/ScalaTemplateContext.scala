package org.scalaide.ui.internal.templates

import org.eclipse.jface.text.templates.TemplateContextType
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.templates.DocumentTemplateContext
import org.eclipse.jface.text.templates.TemplateBuffer
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.Document

class ScalaTemplateContext(contextType: TemplateContextType, document: IDocument, offset: Int, length: Int)
  extends DocumentTemplateContext(contextType, document, offset, length) {

  override def evaluate(template: Template): TemplateBuffer = {
    val buffer = super.evaluate(template)
    // indent to the same level as the insertion point
    indent(buffer)
  }

  def indent(buffer: TemplateBuffer) = {
    import scala.language.postfixOps

    val indentation = indentOfLine(offset)
    val text = buffer.getString()
    val legalLineEnds = document.getLegalLineDelimiters()

    val (breakOffsets, lineEnds) = (for (m <- legalLineEnds.mkString("|").r.findAllMatchIn(text).toList) yield {
      (m.start, m.matched)
    }) unzip

    val actualLineEnd = lineEnds.headOption.getOrElse(document.getLineDelimiter(document.getLineOfOffset(offset)))

    // the template might use any legal line ending
    val result = text.split(legalLineEnds.mkString("|")).mkString(actualLineEnd + indentation)

    def newOffset(x: Int): Int = {
      x + breakOffsets.takeWhile(x >).size * indentation.length
    }

    // update variable offsets in the template, otherwise the
    // UI will highlight the wrong region
    for (v <- buffer.getVariables()) yield {
      v.setOffsets(v.getOffsets().map(newOffset))
    }
    buffer.setContent(result, buffer.getVariables())
    buffer
  }

  private def indentOfLine(offset: Int) = {
    val lineInfo = document.getLineInformationOfOffset(offset)
    val line = document.get(lineInfo.getOffset(), lineInfo.getLength())
    line.takeWhile(Character.isWhitespace)
  }
}
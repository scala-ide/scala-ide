package org.scalaide.core.internal.text

import org.eclipse.jface.text.IDocument
import org.scalaide.core.text.Document
import org.scalaide.core.text.InternalDocument

class TextDocument(private val doc: IDocument) extends Document with InternalDocument {

  override def length: Int =
    doc.getLength()

  override def text: String =
    doc.get()

  override def textRange(start: Int, end: Int): String =
    if (start < 0 || end < start || end > length)
      throw new IndexOutOfBoundsException
    else
      doc.get(start, end-start)

  override def textRangeOpt(start: Int, end: Int): Option[String] =
    if (start < 0 || end < start || end > length)
      None
    else
      Some(doc.get(start, end-start))

  override def lines: Seq[Range] =
    0 until lineCount map lineInformation

  override def lineCount: Int =
    doc.getNumberOfLines()

  override def lineInformation(lineNumber: Int): Range = {
    val l = doc.getLineInformation(lineNumber)
    Range(l.getOffset(), l.getOffset()+l.getLength())
  }

  override def replace(start: Int, end: Int, text: String): Unit =
    doc.replace(start, end-start, text)
}
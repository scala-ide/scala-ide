package org.scalaide.core.internal.text

import org.eclipse.jface.text.IDocument
import org.scalaide.core.text.Document
import org.scalaide.core.text.InternalDocument

class TextDocument(private val doc: IDocument) extends Document with InternalDocument {

  override def apply(i: Int): Char =
    if (i >= 0 && i < length)
      doc.getChar(i)
    else
      throw new IndexOutOfBoundsException

  override def length: Int =
    doc.getLength()

  override def text: String =
    doc.get()

  override def textRange(start: Int, end: Int): String =
    if (isValidRange(start, end))
      doc.get(start, end-start)
    else
      throw new IndexOutOfBoundsException

  override def textRangeOpt(start: Int, end: Int): Option[String] =
    if (isValidRange(start, end))
      Some(doc.get(start, end-start))
    else
      None

  override def lines: Seq[Range] =
    0 until lineCount map lineInformation

  override def lineCount: Int =
    doc.getNumberOfLines()

  override def lineInformation(lineNumber: Int): Range =
    if (lineNumber < 0 || lineNumber >= lineCount)
      throw new IndexOutOfBoundsException
    else {
      val l = doc.getLineInformation(lineNumber)
      Range(l.getOffset(), l.getOffset()+l.getLength())
    }

  override def lineInformationOfOffset(offset: Int): Range =
    if (offset < 0 || offset > length)
      throw new IndexOutOfBoundsException
    else {
      val l = doc.getLineInformationOfOffset(offset)
      Range(l.getOffset(), l.getOffset()+l.getLength())
    }

  override def replace(start: Int, end: Int, text: String): Unit =
    doc.replace(start, end-start, text)

  override def head: Char =
    if (!isEmpty)
      doc.getChar(0)
    else
      throw new IndexOutOfBoundsException

  override def headOpt: Option[Char] =
    if (!isEmpty)
      Some(doc.getChar(0))
    else
      None

  override def tail: String =
    if (!isEmpty)
      doc.get(1, length-1)
    else
      throw new IndexOutOfBoundsException

  override def tailOpt: Option[String] =
    if (!isEmpty)
      Some(doc.get(1, length-1))
    else
      None

  override def init: String =
    if (!isEmpty)
      doc.get(0, length-1)
    else
      throw new IndexOutOfBoundsException

  override def initOpt: Option[String] =
    if (!isEmpty)
      Some(doc.get(0, length-1))
    else
      None

  override def last: Char =
    if (!isEmpty)
      doc.getChar(length-1)
    else
      throw new IndexOutOfBoundsException

  override def lastOpt: Option[Char] =
    if (!isEmpty)
      Some(doc.getChar(length-1))
    else
      None

  private def isEmpty: Boolean =
    length == 0

  private def isValidRange(start: Int, end: Int): Boolean =
    start >= 0 && start <= end && end <= length
}

package org.scalaide.core.internal.text

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.TextUtilities
import org.scalaide.core.text.Document
import org.scalaide.core.text.InternalDocument

class TextDocument(private val doc: IDocument) extends Document with InternalDocument {

  override def apply(i: Int): Char =
    doc.getChar(i)

  override def length: Int =
    doc.getLength()

  override def text: String =
    doc.get()

  override def textRange(start: Int, end: Int): String =
    doc.get(start, end-start)

  override def textRangeOpt(start: Int, end: Int): Option[String] =
    if (isValidRange(start, end))
      Some(doc.get(start, end-start))
    else
      None

  override def lines: Seq[IRegion] =
    0 until lineCount map lineInformation

  override def lineCount: Int =
    doc.getNumberOfLines()

  override def lineInformation(lineNumber: Int): IRegion =
    doc.getLineInformation(lineNumber)

  override def lineInformationOfOffset(offset: Int): IRegion =
    doc.getLineInformationOfOffset(offset)

  override def replace(start: Int, end: Int, text: String): Unit =
    doc.replace(start, end-start, text)

  override def head: Char =
    doc.getChar(0)

  override def headOpt: Option[Char] =
    if (!isEmpty)
      Some(doc.getChar(0))
    else
      None

  override def tail: String =
    doc.get(1, length-1)

  override def tailOpt: Option[String] =
    if (!isEmpty)
      Some(doc.get(1, length-1))
    else
      None

  override def init: String =
    doc.get(0, length-1)

  override def initOpt: Option[String] =
    if (!isEmpty)
      Some(doc.get(0, length-1))
    else
      None

  override def last: Char =
    doc.getChar(length-1)

  override def lastOpt: Option[Char] =
    if (!isEmpty)
      Some(doc.getChar(length-1))
    else
      None

  override def defaultLineDelimiter: String =
    TextUtilities.getDefaultLineDelimiter(doc)

  override def toString(): String =
    text

  private def isEmpty: Boolean =
    length == 0

  private def isValidRange(start: Int, end: Int): Boolean =
    start >= 0 && start <= end && end <= length
}

package org.scalaide.core.text

import org.eclipse.jface.text.IRegion

trait Document {

  doc =>

  def apply(i: Int): Char

  def length: Int

  def text: String

  def textRange(start: Int, end: Int): String

  def textRangeOpt(start: Int, end: Int): Option[String]

  def lines: Seq[IRegion]

  def lineCount: Int

  def lineInformation(lineNumber: Int): IRegion

  def lineInformationOfOffset(offset: Int): IRegion

  def head: Char

  def headOpt: Option[Char]

  def tail: String

  def tailOpt: Option[String]

  def init: String

  def initOpt: Option[String]

  def last: Char

  def lastOpt: Option[Char]

  /**
   * This is either the delimiter of the first line, the platform line delimiter
   * if it is a legal line delimiter or the first one of the legal line
   * delimiters. The default line delimiter should be used when performing
   * document manipulations that span multiple lines. The legal line delimiters
   * usually are "\r", "\n" and "\r\n".
   */
  def defaultLineDelimiter: String
}

private[core] trait InternalDocument extends Document {
  def replace(start: Int, end: Int, text: String): Unit
}

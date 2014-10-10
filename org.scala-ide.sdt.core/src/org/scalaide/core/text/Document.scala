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
}

private[core] trait InternalDocument extends Document {
  def replace(start: Int, end: Int, text: String): Unit
}

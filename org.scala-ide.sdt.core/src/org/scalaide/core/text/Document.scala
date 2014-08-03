package org.scalaide.core.text

trait Document {

  doc =>

  def length: Int

  def text: String

  def textRange(start: Int, end: Int): String

  def textRangeOpt(start: Int, end: Int): Option[String]

  def lines: Seq[Range]

  def lineCount: Int

  def lineInformation(lineNumber: Int): Range

  case class Range(start: Int, end: Int) {
    def length: Int = end-start
    def text: String = doc.textRange(start, end)

    def trim: Range =
      trimLeft.trimRight

    def trimLeft: Range = {
      val s = text
      val len = length

      var i = 0
      while (i < len && Character.isWhitespace(s.charAt(i)))
        i += 1

      Range(start+i, end)
    }

    def trimRight: Range = {
      val s = text
      val len = length

      var i = len-1
      while (i >= 0 && Character.isWhitespace(s.charAt(i)))
        i -= 1

      Range(start, start+i+1)
    }
  }
}

private[core] trait InternalDocument extends Document {
  def replace(start: Int, end: Int, text: String): Unit
}

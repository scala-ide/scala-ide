package org.scalaide.core.text

trait Change

object TextChange {
  def apply(start: Int, end: Int, text: String): TextChange =
    Replace(start, end, text)

  def unapply(change: TextChange): Option[(Int, Int, String)] =
    Some((change.start, change.end, change.text))
}
trait TextChange extends Change {
  def start: Int
  def end: Int
  def text: String
}

case class Add(start: Int, text: String) extends TextChange {
  val end = start
}
case class Replace(start: Int, end: Int, text: String) extends TextChange
case class Remove(start: Int, end: Int) extends TextChange {
  val text = ""
}
package org.scalaide.core.text

trait Change

object TextChange {
  def apply(start: Int, end: Int, text: String): TextChange =
    if (start == end)
      Add(start, text)
    else if (text.isEmpty())
      Remove(start, end)
    else
      Replace(start, end, text)

  def unapply(change: TextChange): Option[(Int, Int, String)] =
    Some((change.start, change.end, change.text))
}
trait TextChange extends Change {
  def start: Int
  def end: Int
  def text: String

  def copy(start: Int = this.start, end: Int = this.end, text: String = this.text): TextChange =
    TextChange(start, end, text)

  def withCursorPos(pos: Int): CursorUpdate =
    CursorUpdate(this, pos)

  def withLinkedModel(exitPosition: Int, positionsGroups: Seq[Seq[(Int, Int)]] = Seq()): LinkedModel =
    LinkedModel(this, exitPosition, positionsGroups)

  override def toString(): String =
    s"""TextChange(start=$start, end=$end, text="$text")"""
}

case class Add(override val start: Int, override val text: String) extends TextChange {
  override val end = start
}
case class Replace(override val start: Int, override val end: Int, override val text: String) extends TextChange
case class Remove(override val start: Int, override val end: Int) extends TextChange {
  override val text = ""
}

case class CursorUpdate(textChange: TextChange, cursorPosition: Int, smartBackspaceEnabled: Boolean = false) extends Change

case class LinkedModel(textChange: TextChange, exitPosition: Int, positionGroups: Seq[Seq[(Int, Int)]] = Seq()) extends Change

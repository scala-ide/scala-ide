package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object CloseStringSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseString],
  name = "Close strings",
  description =
    "Closes a typed opening string literal if necessary."
)

trait CloseString extends AutoEdit {

  override def setting = CloseStringSetting

  override def perform() = {
    rule(textChange) {
      case Add(start, "\"") =>
        if (autoClosingRequired(start))
          Some(Add(start, "\"\"") withLinkedModel (start+2, singleLinkedPos(start+1)))
        else
          None
    }
  }

  def ch(i: Int, c: Char) = {
    val o = textChange.start + i
    o >= 0 && o < document.length && document(o) == c
  }

  def singleLinkedPos(pos: Int): Seq[Seq[(Int, Int)]] =
    Seq(Seq((pos, 0)))

  def autoClosingRequired(offset: Int): Boolean =
    if (offset < document.length)
      !ch(-1, '"') && Character.isWhitespace(document(offset))
    else
      !ch(-1, '"')
}

package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.IDocument
import org.scalaide.core.lexical.ScalaPartitions
import org.scalaide.core.text.TextChange
import org.scalaide.util.eclipse.RegionUtils._

object CloseBacktickSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseBacktick],
  name = "Close backticks",
  description = "Closes a typed opening backtick if necessary.",
  partitions = Set(
    IDocument.DEFAULT_CONTENT_TYPE,
    ScalaPartitions.SCALADOC_CODE_BLOCK
  )
)

trait CloseBacktick extends AutoEdit {

  override def setting = CloseBacktickSetting

  override def perform() = {
    def isPreviousCharacterBacktick = {
      val o = textChange.start - 1
      o >= 0 && document(o) == '`'
    }

    def singleLinkedPos(pos: Int): Seq[Seq[(Int, Int)]] =
      Seq(Seq((pos, 0)))

    def autoClosingRequired(offset: Int): Boolean = {
      val line = document.lineInformationOfOffset(offset)
      val relevantChars = line.text(document).substring(0, offset - line.start)
      if (relevantChars.count(_ == '`') % 2 != 0)
        false
      else if (offset < document.length)
        !isPreviousCharacterBacktick && Character.isWhitespace(document(offset))
      else
        !isPreviousCharacterBacktick
    }

    check(textChange) {
      case TextChange(start, end, "`") ⇒
        if (autoClosingRequired(end))
          Some(TextChange(start, end, "``") withLinkedModel (start + 2, singleLinkedPos(start + 1)))
        else
          None
    }
  }
}

package org.scalaide.extensions
package autoedits

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocument
import org.scalaide.core.lexical.ScalaPartitions
import org.scalaide.core.text.TextChange

object CloseCharSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseChar],
  name = "Close char literals",
  description = "Closes a typed opening char literal if necessary.",
  partitions = Set(
    IDocument.DEFAULT_CONTENT_TYPE,
    ScalaPartitions.SCALADOC_CODE_BLOCK
  )
)

trait CloseChar extends AutoEdit {

  override def setting = CloseCharSetting

  override def perform() = {
    check(textChange) {
      case TextChange(start, end, "'") =>
        if (autoClosingRequired(end))
          Some(TextChange(start, end, "''") withLinkedModel (start+2, singleLinkedPos(start+1)))
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

  def isNested(offset: Int) =
    document.textRangeOpt(offset-1, offset+1) exists (Set("{}", "[]", "()", "<>", "\"\"")(_))

  def autoClosingRequired(offset: Int): Boolean =
    if (offset < document.length)
      !ch(-1, ''') && (Character.isWhitespace(document(offset)) || isNested(offset))
    else
      !ch(-1, ''')

}

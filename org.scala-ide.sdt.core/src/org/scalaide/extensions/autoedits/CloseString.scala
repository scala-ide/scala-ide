package org.scalaide.extensions
package autoedits

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.IDocument
import org.scalaide.core.lexical.ScalaPartitions
import org.scalaide.core.text.Add

object CloseStringSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CloseString],
  name = "Close strings",
  description = "Closes a typed opening string literal if necessary.",
  partitions = Set(
    IDocument.DEFAULT_CONTENT_TYPE,
    ScalaPartitions.SCALA_MULTI_LINE_STRING,
    ScalaPartitions.SCALADOC_CODE_BLOCK,
    IJavaPartitions.JAVA_DOC,
    IJavaPartitions.JAVA_MULTI_LINE_COMMENT
  )
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

  def isNested(offset: Int) =
    document.textRangeOpt(offset-1, offset+1) exists (Set("{}", "[]", "()", "<>", "\"\"")(_))

  def autoClosingRequired(offset: Int): Boolean =
    if (offset < document.length)
      !ch(-1, '"') && (Character.isWhitespace(document(offset)) || isNested(offset))
    else
      !ch(-1, '"')
}

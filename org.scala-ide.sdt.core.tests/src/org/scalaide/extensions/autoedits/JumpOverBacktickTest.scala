package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class JumpOverBacktickTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new JumpOverBacktick {
    override val document = doc
    override val textChange = change
  }

  @Test
  def jump_over_closing() =
    "^`" becomes "`^" after Add("`")

}

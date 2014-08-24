package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.JumpOverClosingBracket

object JumpOverClosingBracketCreator {
  def create(doc: Document, change: TextChange): JumpOverClosingBracket =
    new JumpOverClosingBracket {
      override val document = doc
      override val textChange = change
    }
}

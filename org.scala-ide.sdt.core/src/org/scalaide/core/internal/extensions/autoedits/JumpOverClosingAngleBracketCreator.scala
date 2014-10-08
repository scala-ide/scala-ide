package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.JumpOverClosingAngleBracket

object JumpOverClosingAngleBracketCreator {
  def create(doc: Document, change: TextChange): JumpOverClosingAngleBracket =
    new JumpOverClosingAngleBracket {
      override val document = doc
      override val textChange = change
    }
}

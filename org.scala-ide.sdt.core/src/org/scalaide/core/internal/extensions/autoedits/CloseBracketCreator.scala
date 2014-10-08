package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.CloseBracket

object CloseBracketCreator {
  def create(doc: Document, change: TextChange): CloseBracket =
    new CloseBracket {
      override val document = doc
      override val textChange = change
    }
}

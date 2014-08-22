package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.CloseParenthesis

object CloseParenthesisCreator {
  def create(doc: Document, change: TextChange): CloseParenthesis =
    new CloseParenthesis {
      override val document = doc
      override val textChange = change
    }
}

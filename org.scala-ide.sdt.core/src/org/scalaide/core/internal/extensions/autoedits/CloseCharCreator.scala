package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.CloseChar

object CloseCharCreator {
  def create(doc: Document, change: TextChange): CloseChar =
    new CloseChar {
      override val document = doc
      override val textChange = change
    }
}

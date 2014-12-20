package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.SurroundBlock

object SurroundBlockCreator {
  def create(doc: Document, change: TextChange): SurroundBlock =
    new SurroundBlock {
      override val document = doc
      override val textChange = change
    }
}

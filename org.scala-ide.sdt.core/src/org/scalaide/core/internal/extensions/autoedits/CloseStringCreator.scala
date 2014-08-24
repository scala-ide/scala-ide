package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.CloseString

object CloseStringCreator {
  def create(doc: Document, change: TextChange): CloseString =
    new CloseString {
      override val document = doc
      override val textChange = change
    }
}

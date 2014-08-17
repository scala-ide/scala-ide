package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.ConvertToUnicode

object ConvertToUnicodeCreator {
  def create(doc: Document, change: TextChange): ConvertToUnicode =
    new ConvertToUnicode {
      override val document = doc
      override val textChange = change
    }
}

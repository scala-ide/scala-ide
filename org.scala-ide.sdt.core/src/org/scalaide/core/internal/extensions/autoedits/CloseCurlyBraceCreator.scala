package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.CloseCurlyBrace

object CloseCurlyBraceCreator {
  def create(doc: Document, change: TextChange): CloseCurlyBrace =
    new CloseCurlyBrace {
      override val document = doc
      override val textChange = change
    }
}

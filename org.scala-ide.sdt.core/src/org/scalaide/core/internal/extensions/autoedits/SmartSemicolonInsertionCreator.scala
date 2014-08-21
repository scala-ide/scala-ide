package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.SmartSemicolonInsertion

object SmartSemicolonInsertionCreator {
  def create(doc: Document, change: TextChange): SmartSemicolonInsertion =
    new SmartSemicolonInsertion {
      override val document = doc
      override val textChange = change
    }
}

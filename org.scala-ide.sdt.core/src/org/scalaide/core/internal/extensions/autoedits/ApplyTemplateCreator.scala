package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.ApplyTemplate

object ApplyTemplateCreator {
  def create(doc: Document, change: TextChange): ApplyTemplate =
    new ApplyTemplate {
      override val document = doc
      override val textChange = change
    }
}

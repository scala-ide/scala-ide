package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.text.Document
import org.scalaide.extensions.saveactions.AutoFormatting

object AutoFormattingCreator {
  def create(doc: Document): AutoFormatting =
    new AutoFormatting {
      override val document = doc
    }
}

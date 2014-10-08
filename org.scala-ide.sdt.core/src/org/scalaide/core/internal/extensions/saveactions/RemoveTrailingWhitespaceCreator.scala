package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.text.Document
import org.scalaide.extensions.DocumentSupport
import org.scalaide.extensions.saveactions.RemoveTrailingWhitespace

object RemoveTrailingWhitespaceCreator {
  def create(doc: Document): RemoveTrailingWhitespace =
    new RemoveTrailingWhitespace {
      override val document = doc
    }
}
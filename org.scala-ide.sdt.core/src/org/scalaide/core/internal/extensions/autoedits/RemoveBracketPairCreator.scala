package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.RemoveBracketPair

object RemoveBracketPairCreator {
  def create(doc: Document, change: TextChange): RemoveBracketPair =
    new RemoveBracketPair {
      override val document = doc
      override val textChange = change
    }
}

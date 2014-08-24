package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.RemoveCurlyBracePair

object RemoveCurlyBracePairCreator {

  def create(doc: Document, change: TextChange): RemoveCurlyBracePair =
    new RemoveCurlyBracePair {
      override val document = doc
      override val textChange = change
    }
}
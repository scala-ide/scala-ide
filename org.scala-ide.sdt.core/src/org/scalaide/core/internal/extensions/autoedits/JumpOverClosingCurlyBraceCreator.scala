package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits.JumpOverClosingCurlyBrace

object JumpOverClosingCurlyBraceCreator {

  def create(doc: Document, change: TextChange): JumpOverClosingCurlyBrace =
    new JumpOverClosingCurlyBrace {
      override val document = doc
      override val textChange = change
    }
}

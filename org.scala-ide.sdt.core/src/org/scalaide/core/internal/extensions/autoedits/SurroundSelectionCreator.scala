package org.scalaide.core.internal.extensions.autoedits

import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.autoedits._

object SurroundSelectionWithStringCreator {
  def create(doc: Document, change: TextChange): SurroundSelectionWithString =
    new SurroundSelectionWithString {
      override val document = doc
      override val textChange = change
    }
}

object SurroundSelectionWithParenthesesCreator {
  def create(doc: Document, change: TextChange): SurroundSelectionWithParentheses =
    new SurroundSelectionWithParentheses {
      override val document = doc
      override val textChange = change
    }
}

object SurroundSelectionWithBracesCreator {
  def create(doc: Document, change: TextChange): SurroundSelectionWithBraces =
    new SurroundSelectionWithBraces {
      override val document = doc
      override val textChange = change
    }
}

object SurroundSelectionWithBracketsCreator {
  def create(doc: Document, change: TextChange): SurroundSelectionWithBrackets =
    new SurroundSelectionWithBrackets {
      override val document = doc
      override val textChange = change
    }
}

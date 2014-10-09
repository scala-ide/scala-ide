package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.text.Document
import org.scalaide.extensions.saveactions.RemoveDuplicatedEmptyLines

object RemoveDuplicatedEmptyLinesCreator {
  def create(doc: Document): RemoveDuplicatedEmptyLines =
    new RemoveDuplicatedEmptyLines {
      override val document = doc
    }
}

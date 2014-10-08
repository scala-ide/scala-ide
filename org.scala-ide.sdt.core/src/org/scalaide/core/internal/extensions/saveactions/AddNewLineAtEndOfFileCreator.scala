package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.text.Document
import org.scalaide.extensions.DocumentSupport
import org.scalaide.extensions.saveactions.AddNewLineAtEndOfFile

object AddNewLineAtEndOfFileCreator {
  def create(doc: Document): AddNewLineAtEndOfFile =
    new AddNewLineAtEndOfFile {
      override val document = doc
    }
}
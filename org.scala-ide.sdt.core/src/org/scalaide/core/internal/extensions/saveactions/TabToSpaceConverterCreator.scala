package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.text.Document
import org.scalaide.extensions.saveactions.TabToSpaceConverter

object TabToSpaceConverterCreator {
  def create(doc: Document): TabToSpaceConverter =
    new TabToSpaceConverter {
      override val document = doc
    }
}

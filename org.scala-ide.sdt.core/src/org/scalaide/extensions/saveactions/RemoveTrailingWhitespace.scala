package org.scalaide.extensions
package saveactions

import org.scalaide.core.text.Remove

/**
 * Removes the trailing whitespace of the entire document this save action is
 * invoked on.
 */
trait RemoveTrailingWhitespace extends SaveAction with DocumentSupport {

  def perform() = {
    document.lines flatMap { line =>
      val trimmed = line.trimRight

      if (trimmed.length != line.length)
        Seq(Remove(trimmed.end, line.end))
      else
        Seq()
    }
  }
}
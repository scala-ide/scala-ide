package org.scalaide.extensions
package saveactions

import org.scalaide.core.text.Add

/**
 * Adds a new line at the end of a file if none exists.
 */
trait AddNewLineAtEndOfFile extends SaveAction with DocumentSupport {

  def perform() =
    if (document.last != '\n')
      Seq(Add(document.length, "\n"))
    else
      Seq()
}
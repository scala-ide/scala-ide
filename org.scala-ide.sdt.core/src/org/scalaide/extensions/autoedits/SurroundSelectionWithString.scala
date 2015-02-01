package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.core.text.Replace
import org.scalaide.util.eclipse.RegionUtils._

object SurroundSelectionWithStringSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[SurroundSelectionWithString],
  name = "Surround selection with \"quotes\"",
  description = "Automatically surrounds a selection with quotes when a quotation mark is typed."
)

trait SurroundSelectionWithString extends AutoEdit {

  def setting = SurroundSelectionWithStringSetting

  def perform() = {
    check(textChange) {
      case Add(start, "\"") ⇒
        subcheck(textSelection) {
          case sel if sel.length > 0 ⇒
            Replace(sel.start, sel.end, '"' + sel.getText + '"')
        }
    }
  }
}

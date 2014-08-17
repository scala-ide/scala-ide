package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add
import org.scalaide.core.text.Replace

object ConvertToUnicodeSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[ConvertToUnicode],
  name = "Convert methods to their unicode representation",
  description =
    "Converts methods to their unicode representation if one exists." +
    " This applies to =>, -> and <- which are converted to ⇒, → and ← respectively."
)

trait ConvertToUnicode extends AutoEdit {

  override def setting = ConvertToUnicodeSetting

  override def perform() = {
    rule(textChange) {
      case Add(start, text) if text.size == 1 =>
        subrule(document(start-1)+text) {
          case "=>" => Replace(start-1, start, "⇒")
          case "->" => Replace(start-1, start, "→")
          case "<-" => Replace(start-1, start, "←")
        }
    }
  }
}

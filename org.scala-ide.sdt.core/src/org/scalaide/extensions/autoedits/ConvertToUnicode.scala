package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.TextChange

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
    check(textChange) {
      case TextChange(start, end, text) if text.size == 1 && start > 0 =>
        subcheck(document(start-1)+text) {
          case "=>" => TextChange(start-1, end, "⇒")
          case "->" => TextChange(start-1, end, "→")
          case "<-" => TextChange(start-1, end, "←")
        }

      case TextChange(start, end, text) =>
        subcheck(text) {
          case "=>" => TextChange(start, end, "⇒") withCursorPos start+1
          case "->" => TextChange(start, end, "→") withCursorPos start+1
          case "<-" => TextChange(start, end, "←") withCursorPos start+1
        }
    }
  }
}

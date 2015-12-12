package org.scalaide.extensions
package autoedits

import org.scalaide.core.text.Add

object JumpOverBacktickSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[JumpOverBacktick],
  name = "Jump over closing backtick",
  description = ExtensionSetting.formatDescription(
    """|If a backtick is typed but at the cursor position a backtick \
       |already exists, the cursor jumps over this backtick without adding \
       |another one.""")
)

trait JumpOverBacktick extends AutoEdit {

  override def setting = JumpOverBacktickSetting

  override def perform() = {
    check(textChange) {
      case Add(start, "`") ⇒
        lookupChar(0) {
          case '`' ⇒
            Add(start, "") withCursorPos start + 1
        }
    }
  }
}

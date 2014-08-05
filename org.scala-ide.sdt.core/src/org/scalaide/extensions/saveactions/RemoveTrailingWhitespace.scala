package org.scalaide.extensions
package saveactions

import org.scalaide.core.text.Remove

object RemoveTrailingWhitespaceSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveTrailingWhitespace],
  name = "Remove trailing whitespace",
  description = "Removes trailing whitespace of the entire document.",
  codeExample = """|class Test {  $
                   |  val value = 0    $
                   |}""".stripMargin.replaceAll("\\$", "")
)

/**
 * Removes the trailing whitespace of the entire document this save action is
 * invoked on.
 */
trait RemoveTrailingWhitespace extends SaveAction with DocumentSupport {

  override def setting = RemoveTrailingWhitespaceSetting

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
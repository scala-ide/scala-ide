package org.scalaide.extensions
package saveactions

import org.scalaide.core.text.Add

object AddNewLineAtEndOfFileSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AddNewLineAtEndOfFile],
  name = "Add new line at end of file",
  description = "Adds a new line at the end of a file if none exists.",
  textBefore = """|class Test {
                  |  val value = 0
                  |}""".stripMargin,
  textAfter = """|class Test {
                 |  val value = 0
                 |}
                 |""".stripMargin
)

/**
 * Adds a new line at the end of a file if none exists.
 */
trait AddNewLineAtEndOfFile extends SaveAction with DocumentSupport {

  override def setting = AddNewLineAtEndOfFileSetting

  def perform() =
    if (document.last != '\n')
      Seq(Add(document.length, "\n"))
    else
      Seq()
}
package org.scalaide.extensions
package saveactions

import org.scalaide.core.text.Remove
import org.scalaide.util.eclipse.RegionUtils._

object RemoveDuplicatedEmptyLinesSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[RemoveDuplicatedEmptyLines],
  name = "Remove duplicated empty lines",
  description = "Removes duplicated (consecutive) empty lines.",
  codeExample = """|class Test {
                   |
                   |
                   |
                   |  val value = 0
                   |
                   |}""".stripMargin
)

trait RemoveDuplicatedEmptyLines extends SaveAction with DocumentSupport {

  override def setting = RemoveDuplicatedEmptyLinesSetting

  override def perform() = {
    val emptyLines = document.lines.zipWithIndex.filter {
      case (r, _) => r.trimRight(document).length == 0
    }

    val removedLines = emptyLines.sliding(2) flatMap {
      case Seq((l1, i1), (l2, i2)) =>
        if (i1+1 == i2)
          Seq(Remove(l1.start, l1.end+1))
        else
          Seq()
    }

    removedLines.toList
  }
}

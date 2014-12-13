package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.IRegion
import org.scalaide.core.text.Add
import org.scalaide.core.text.Replace
import org.scalaide.util.eclipse.RegionUtils._

object CreateMultiplePackageDeclarationsSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[CreateMultiplePackageDeclarations],
  name = "Create new package declaration on line break",
  description = ExtensionSetting.formatDescription(
    """|In case there exists the package declaration
       |
       |    package a.b.c.d.^e
       |
       |and the cursor is placed at the position of the ^ sign, one \
       |can press enter to get
       |
       |    package a.b.c.d
       |    package e^
       |""")
)

trait CreateMultiplePackageDeclarations extends AutoEdit {

  override def setting = CreateMultiplePackageDeclarationsSetting

  override def perform = {
    rule(textChange) {
      case Add(start, "\n") =>
        val line = document.lineInformationOfOffset(start)
        pkgName(start, line) map { pkgName =>
          Replace(start-1, start, "\npackage ") withCursorPos line.end+"package ".length()
        }
    }
  }

  private def pkgName(offset: Int, line: IRegion): Option[String] = {
    val caretPos = offset-line.start
    val PackageDetector = """package\s+\w+\.(?:\w+\.)*\^(\w+)""".r

    new StringBuilder(line.text(document)).insert(caretPos, '^').toString() match {
      case PackageDetector(s) => Some(s)
      case _                  => None
    }
  }
}

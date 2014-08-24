package org.scalaide.extensions
package saveactions

import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.core.text.Replace

import scalariform.formatter.preferences._

object TabToSpaceConverterSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[TabToSpaceConverter],
  name = "Convert tabs to spaces",
  description = "Converts all tabs to spaces in the entire document.",
  codeExample = """|class Test {
                   |\tval value1 = 0
                   |  val value2 = 0
                   |}""".stripMargin.replaceAll("\\\\t", "\t")
)

trait TabToSpaceConverter extends SaveAction with DocumentSupport {

  override def setting = TabToSpaceConverterSetting

  override def perform() = {
    val tabWidth = ScalaPlugin.prefStore.getInt(IndentSpaces.eclipseKey)
    val spaces = " "*tabWidth
    val len = document.length
    val text = document.text

    var i = 0
    var changes = List[Replace]()
    while (i < len) {
      if (text.charAt(i) == '\t')
        changes ::= Replace(i, i+1, spaces)
      i += 1
    }

    changes
  }
}

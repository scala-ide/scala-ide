package org.scalaide.extensions
package saveactions

import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences
import org.scalaide.core.text.Replace

import scalariform.formatter.ScalaFormatter
import scalariform.utils.TextEdit

object AutoFormattingSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AutoFormatting],
  name = "Auto formatting",
  description = "Formats the entire document based on the configuration options in \"Scala > Editor > Formatter\".",
  codeExample = """|class C {
                   |  def f = {
                   |    val a=0
                   |      val b =5
                   |     a +b
                   |  }
                   |}
                   |""".stripMargin
)

trait AutoFormatting extends SaveAction with DocumentSupport {

  def setting = AutoFormattingSetting

  def perform() = {
    val formatted = ScalaFormatter.formatAsEdits(
        document.text,
        FormatterPreferences.getPreferences(ScalaPlugin.prefStore))
    formatted map { case TextEdit(pos, len, text) => Replace(pos, pos+len, text) }
  }
}

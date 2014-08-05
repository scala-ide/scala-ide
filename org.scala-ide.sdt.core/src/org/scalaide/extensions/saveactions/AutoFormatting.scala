package org.scalaide.extensions
package saveactions

import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences
import org.scalaide.core.text.Replace

import scalariform.formatter.ScalaFormatter

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
    val formatted = ScalaFormatter.format(
        document.text,
        FormatterPreferences.getPreferences(ScalaPlugin.prefStore))
    Seq(Replace(0, document.length, formatted))
  }
}
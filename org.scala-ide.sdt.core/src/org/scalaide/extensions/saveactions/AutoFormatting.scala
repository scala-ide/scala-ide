package org.scalaide.extensions
package saveactions

import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences
import org.scalaide.core.text.Replace
import org.scalaide.util.internal.eclipse.ProjectUtils

import scalariform.formatter.ScalaFormatter
import scalariform.utils.TextEdit

object AutoFormattingSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AutoFormatting],
  name = "Auto formatting",
  description =
    """|Formats the entire document based on the configuration options in \
       |"Scala > Editor > Formatter". If project specific formatting is enabled, \
       |these formatting options are considered instead.
       |""".stripMargin.replaceAll("\\\\\n", ""),
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

  override def setting = AutoFormattingSetting

  override def perform() = {
    val prefs = ProjectUtils.resourceOfSelection().map {
      r => FormatterPreferences.getPreferences(r.getProject)
    }.getOrElse(FormatterPreferences.getPreferences(IScalaPlugin().getPreferenceStore()))

    val formatted = ScalaFormatter.formatAsEdits(document.text, prefs)
    formatted map { case TextEdit(pos, len, text) => Replace(pos, pos+len, text) }
  }
}

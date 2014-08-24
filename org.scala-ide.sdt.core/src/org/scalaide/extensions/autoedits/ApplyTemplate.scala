package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.templates.GlobalTemplateVariables
import org.eclipse.jface.text.templates.Template
import org.eclipse.jface.text.templates.TemplateBuffer
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.text.Add
import org.scalaide.core.text.LinkedModel
import org.scalaide.core.text.Replace
import org.scalaide.ui.internal.templates.ScalaTemplateContext
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.scalaide.util.internal.ScalaWordFinder

object ApplyTemplateSetting extends AutoEditSetting(
  id = ExtensionSetting.fullyQualifiedName[ApplyTemplate],
  name = "Insert template into editor whenever its name is written",
  description =
    """|The Scala editor provides the functionality of "Templates", which can be \
       |used by selecting them in the code completion dialog after typing their \
       |name. However, this approach is slow and requires user interaction. This \
       |auto edit aims at making templates as easy to use as possible. When enabled, \
       |it applies a template immediatelly after its name is typed and the tab key \
       |is pressed.
       |
       |For example, if there exists the template
       |
       |    ${value} match {
       |      case ${caseValue} => ${cursor}
       |    }
       |
       |whose name is `match`, one can immediately insert the content of this \
       |template into the open editor by simpley typing `match` and then pressing \
       |the tab key.
       |
       |The editor creates even creates a liked model, which allows to jump between \
       |the sections marked with ${}. If enter is pressed while the linked model \
       |is still active, the cursor jumps immediately to the section marked \
       |by ${cursor}.
       |""".stripMargin.replaceAll("\\\\\n", "")
)

trait ApplyTemplate extends AutoEdit {

  override def setting = ApplyTemplateSetting

  override def perform() = {
    rule(textChange) {
      case Add(start, "\t") =>
        val r = ScalaWordFinder.findWord(document.text, start)
        val word = document.textRange(r.getOffset(), r.getOffset()+r.getLength())

        findTemplateByName(word) map { template =>
          applyTemplate(template, start-word.length(), start)
        }
    }
  }

  def applyTemplate(template: Template, start: Int, end: Int): LinkedModel = {
    val tb = mkTemplateBuffer(template, start)
    val vars = tb.getVariables()

    val positionGroups = vars filter (_.getType() != GlobalTemplateVariables.Cursor.NAME) map { v =>
      val len = v.getDefaultValue().length()
      v.getOffsets().toList map (_+start -> len)
    }

    val cursorPos = start + vars
        .find(_.getType() == GlobalTemplateVariables.Cursor.NAME)
        .map(_.getOffsets().head)
        .getOrElse(tb.getString().length())

    Replace(start, end, tb.getString()) withLinkedModel(cursorPos, positionGroups)
  }

  def findTemplateByName(name: String): Option[Template] =
    Option(ScalaPlugin.plugin.templateManager.templateStore.findTemplate(name))

  def mkTemplateBuffer(template: Template, offset: Int): TemplateBuffer = {
    val line = document.lineInformationOfOffset(offset)
    // we add current line to get its indentation
    val doc = new Document(line.text)
    val relOffset = offset-line.start
    val ctx = mkTemplateContext(doc, relOffset)
    ctx.evaluate(template)
  }

  def mkTemplateContext(doc: IDocument, offset: Int): ScalaTemplateContext = {
    val tm = new ScalaTemplateManager()
    val ctxType = tm.contextTypeRegistry.getContextType(tm.CONTEXT_TYPE)
    val ctx = new ScalaTemplateContext(ctxType, doc, offset, 0)
    ctx
  }

}

package org.scalaide.extensions.autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.eclipse.jface.text.templates.Template

class ApplyTemplateTest extends AutoEditTests {

  override def autoEdit(doc: Document, tc: TextChange) = new ApplyTemplate {
    override val document = doc
    override val textChange = tc

    val templates = Map(
      "match" -> """|${value} match {
                    |  case ${caseValue} => ${cursor}
                    |}""".stripMargin,
      "tcatch" -> """|try {
                     |  ${cursor}
                     |} catch {
                     |  case ${t}: ${Throwable} => ${t}.printStackTrace() // TODO: handle error
                     |}
                     |""".stripMargin)

    // provide templates statically in order to not depend on existing templates
    override def findTemplateByName(name: String): Option[Template] = {
      templates get name map { pattern =>
        new Template(name, "description", "contextTypeId", pattern, true)
      }
    }
  }

  @Test
  def add_tab_to_document_if_no_template_found() =
    "f^" becomes "f\t^" after Add("\t")

  @Test
  def apply_match_template() = """
    class X {
      def f(i: Int) = {
        match^
      }
    }
    """ becomes """
    class X {
      def f(i: Int) = {
        [[value]] match {
          case [[caseValue]] => ^
        }
      }
    }
    """ after Add("\t")

  @Test
  def apply_tcatch_template() = """
    class X {
      def f(i: Int) = {
        tcatch^
      }
    }
    """ becomes """
    class X {
      def f(i: Int) = {
        try {
          ^
        } catch {
          case [[t]]: [[Throwable]] => [[t]].printStackTrace() // TODO: handle error
        }
      }
    }
    """ after Add("\t")
}

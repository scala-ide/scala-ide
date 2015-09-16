package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class CreateMultiplePackageDeclarationsTest extends AutoEditTests {

  override def autoEdit(doc: Document, tc: TextChange) = new CreateMultiplePackageDeclarations {
    override val document = doc
    override val textChange = tc
  }

  val newline = Add("\n")

  @Test
  def create_pkg_declaration_on_newline_after_last_dot() = """|
    |package a.b.c.d.^e
    |""".stripMargin becomes """|
    |package a.b.c.d
    |package e^
    |""".stripMargin after newline

  @Test
  def normal_linebreak_if_cursor_position_is_invalid() = """|
    |package a.b.^c.d.e
    |""".stripMargin becomes """|
    |package a.b.
    |^c.d.e
    |""".stripMargin after newline
}

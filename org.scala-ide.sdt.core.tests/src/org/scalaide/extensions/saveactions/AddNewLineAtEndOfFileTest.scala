package org.scalaide.extensions.saveactions

import org.junit.Test
import org.scalaide.core.text.Document

class AddNewLineAtEndOfFileTest extends SaveActionTests {

  override def saveAction(doc: Document) = new AddNewLineAtEndOfFile {
    override val document = doc
  }

  @Test
  def add_new_line_when_none_exists() = """|^
    |class X {
    |}""".stripMargin becomes """|^
    |class X {
    |}
    |""".stripMargin after SaveEvent

  @Test
  def add_no_new_line_when_it_already_exists() = """|^
    |class X {
    |}
    |""".stripMargin becomes """|^
    |class X {
    |}
    |""".stripMargin after SaveEvent
}

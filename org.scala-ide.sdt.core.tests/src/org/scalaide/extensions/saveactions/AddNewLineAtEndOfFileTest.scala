package org.scalaide.extensions.saveactions

import org.junit.Test

class AddNewLineAtEndOfFileTest extends SaveActionTests { self =>

  override def saveAction = new AddNewLineAtEndOfFile {
    override val document = self.document
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

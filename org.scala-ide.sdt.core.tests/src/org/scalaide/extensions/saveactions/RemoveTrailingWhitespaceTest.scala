package org.scalaide.extensions.saveactions

import org.junit.Test
import org.scalaide.core.text.Document

class RemoveTrailingWhitespaceTest extends DocumentSaveActionTests {

  override def saveAction(doc: Document) = new RemoveTrailingWhitespace {
    override val document = doc
  }

  @Test
  def trailing_whitespace_is_removed() = """|^
    |class X {
    |  def hello = {    $
    |    val x = 0   $
    |    $
    |    val y = 1   $
    |    $
    |    x + y
    |  }
    |}
    |   $
    |""".stripMargin becomes """|^
    |class X {
    |  def hello = {
    |    val x = 0
    |
    |    val y = 1
    |
    |    x + y
    |  }
    |}
    |
    |""".stripMargin after SaveEvent
}

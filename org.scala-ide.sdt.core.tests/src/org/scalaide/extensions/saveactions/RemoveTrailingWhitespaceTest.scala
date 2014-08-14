package org.scalaide.extensions.saveactions

import org.junit.Test

class RemoveTrailingWhitespaceTest extends SaveActionTests { self =>

  override def saveAction = new RemoveTrailingWhitespace {
    override val document = self.document
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

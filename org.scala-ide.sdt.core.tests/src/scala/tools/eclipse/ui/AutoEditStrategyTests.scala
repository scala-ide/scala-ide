package scala.tools.eclipse.ui

import org.junit.Assert._
import org.eclipse.jface.text.DocumentCommand

object AutoEditStrategyTests {
  class TestCommand(cOffset: Int, cLength: Int, cText: String, cCaretOffset: Int, cShiftsCaret: Boolean, cDoIt: Boolean) extends DocumentCommand {
    caretOffset = cCaretOffset
    doit = cDoIt
    length = cLength
    offset = cOffset
    text = cText
    shiftsCaret = cShiftsCaret
  }

  def checkCommand(offset: Int, length: Int, text: String, caretOffset: Int, shiftsCaret: Boolean, doit: Boolean, command: DocumentCommand) {
    assertEquals("Bad resulting offset", offset, command.offset)
    assertEquals("Bad resulting lenght", length, command.length)
    assertEquals("Bad resulting text", text, command.text)
    assertEquals("Bad resulting carretOffset", caretOffset, command.caretOffset)
    assertEquals("Bad resulting shiftsCaret", shiftsCaret, command.shiftsCaret)
    assertEquals("Bad resulting doit", doit, command.doit)
  }

}
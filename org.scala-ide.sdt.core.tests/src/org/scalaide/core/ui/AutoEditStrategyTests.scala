package org.scalaide.core.ui

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.mockito.Mockito._

abstract class AutoEditStrategyTests extends TextEditTests with EclipseDocumentSupport {

  val strategy: IAutoEditStrategy

  val prefStore = mock(classOf[IPreferenceStore])

  def enable(property: String, enable: Boolean) {
    when(prefStore.getBoolean(property)).thenReturn(enable)
  }

  def setIntPref(property: String, value: Int) {
    when(prefStore.getInt(property)).thenReturn(value)
  }

  def enabled(property: String)(f: => Unit) = {
    enable(property, true)
    f
  }

  def disabled(property: String)(f: => Unit) = {
    enable(property, false)
    f
  }

  /**
   * In the following there comes an explanation on how [[org.eclipse.jface.text.DocumentCommand]]
   * works, whose logic is rather complex. Because of the complexity and also because
   * of missing comprehensive documentation the following is based on reading and
   * debugging the source code, it must not be correct:
   *
   * - The `doit` field is updated by the logic that sends the command to the auto
   *   edit strategies. It is set to true when a command is considered to be handled,
   *   i.e. it should not be changed again. The test suite doesn't consider this.
   * - The `offset`, `length` and `text` fields should be clear.
   * - The `caretOffset` is initially set to -1 and has to be set to a different
   *   value when it should be considered. This field is strongly coupled to
   *   `fCommands` which are only considered when `caretOffset` is >= 0. When
   *   `caretOffset` is not changed the cursor is automatically shifted (and when
   *   `shiftsCaret` is set to true, which is initially set to true).
   * - `fCommands` hold a list of commands that should be executed in addition to
   *   the outer command. It is anough to access `getCommandIterator` to iterate
   *   over the command and all of its subcommands. When a command is added to the
   *   outer command and `caretOffset` is changed as well, one probably also wants
   *   to set `shiftCaret` to false. Otherwise the caret position is shifted by the
   *   length of the inserted text of the subcommand.
   * - `getCommandIterator` returns the commands in LIFO, with exception of the
   *   outer command which is always the first one.
   * - One command is used for all auto edit trategies, this means that a auto
   *   edit strategy always needs to consider that a command could be modified by
   *   a auto edit strategy that is invoked earlier.
   * - It is possible to create an auto edit strategy inside of another auto edit
   *   strategy and add it to the text editor. It will be invoked by the text
   *   editor later but I suggest not to use this 'feature'.
   */
  class TestCommand(cOffset: Int, cLength: Int, cText: String, cCaretOffset: Int) extends DocumentCommand {
    caretOffset = cCaretOffset
    doit = true
    length = cLength
    offset = cOffset
    text = cText
    shiftsCaret = true
  }

  /** Adds the given string at the position of the caret */
  case class Add(str: String) extends Operation {
    def execute() = {
      val cmd = new TestCommand(caretOffset, 0, str, -1)
      strategy.customizeDocumentCommand(doc, cmd)

      /*
       * The method ``DocumentCommand.execute(IDocument)`` is package private,
       * thus we have to access it with Reflection.
       */
      val m = classOf[DocumentCommand].getDeclaredMethod("execute", classOf[IDocument])
      m.setAccessible(true)
      m.invoke(cmd, doc)

      caretOffset = if (cmd.caretOffset > 0) cmd.caretOffset else cmd.offset + cmd.text.length
    }
  }

  /** Removes the given string before the position of the caret */
  case class Remove(str: String) extends Operation {
    def execute() = {
      val actual = doc.get().substring(caretOffset - str.length, caretOffset)
      require(str == actual, "removeable content does not equal to the declared content")

      val cmd = new TestCommand(caretOffset - str.length, str.length, "", -1)
      strategy.customizeDocumentCommand(doc, cmd)

      doc.replace(cmd.offset, cmd.length, "")
      caretOffset = if (cmd.caretOffset > 0) cmd.caretOffset else cmd.offset + cmd.text.length
    }
  }
}
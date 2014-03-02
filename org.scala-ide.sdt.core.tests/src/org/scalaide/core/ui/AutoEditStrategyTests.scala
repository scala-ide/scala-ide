package org.scalaide.core.ui

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension3
import org.junit.ComparisonFailure
import org.mockito.Mockito._
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner

object AutoEditStrategyTests {

  class TestCommand(cOffset: Int, cLength: Int, cText: String, cCaretOffset: Int) extends DocumentCommand {
    caretOffset = cCaretOffset
    doit = true
    length = cLength
    offset = cOffset
    text = cText
    shiftsCaret = true
  }

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
}

abstract class AutoEditStrategyTests(strategy: IAutoEditStrategy) {

  /**
   * Creates the following DSL for a test:
   * {{{
   * @Test
   * def test() {
   *   "object A^" becomes "object AB^" after Add("B")
   * }
   * }}}
   */
  implicit class StringAsTest(input: String) {
    def becomes(expectedOutput: String) = input -> expectedOutput
  }
  implicit class TestExecutor(testData: (String, String)) {
    def after(operation: Operation) = test(testData._1, testData._2, operation)
  }

  import AutoEditStrategyTests._

  sealed abstract class Operation
  /** Adds the given string at the position of the caret */
  case class Add(s: String) extends Operation
  /** Removes the given string before the position of the caret */
  case class Remove(s: String) extends Operation

  /**
   * Tests if the input string is equal to the expected output after it is applied
   * to the given operation.
   *
   * For each input and output string, there must be set the cursor position
   * which is denoted by a ^ sign and must occur once.
   *
   * If the operation is `Remove` the string of this operation must be placed
   * before the caret in the input string.
   *
   * Sometimes it can happen that the input or output must contain trailing
   * white spaces. If this is the case then a $ sign must be set to the position
   * after the expected number of white spaces.
   *
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
  def test(input: String, expectedOutput: String, operation: Operation): Unit = {
    require(input.count(_ == '^') == 1, "the cursor in the input isn't set correctly")
    require(expectedOutput.count(_ == '^') == 1, "the cursor in the expected output isn't set correctly")

    val inputWithoutDollarSigns = input.filterNot(_ == '$')
    val textOffset = inputWithoutDollarSigns.indexOf('^')
    val doc = {
      val inputWithoutCursor = inputWithoutDollarSigns.filterNot(_ == '^')
      val doc = new Document(inputWithoutCursor)
      val partitioner = new ScalaDocumentPartitioner

      doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
      doc.setDocumentPartitioner(IDocumentExtension3.DEFAULT_PARTITIONING, partitioner)
      partitioner.connect(doc)
      doc
    }

    operation match {
      case Remove(declared) =>
        val actual = doc.get().substring(textOffset - declared.length, textOffset)
        require(declared == actual, "removeable content does not equal to the declared content")
      case _ =>
    }

    val cmd = operation match {
      case Add(s)    => new TestCommand(textOffset, 0, s, -1)
      case Remove(s) => new TestCommand(textOffset - s.length, s.length, "", -1)
    }

    strategy.customizeDocumentCommand(doc, cmd)

    operation match {
      case Add(s) =>
        import collection.JavaConverters._
        val cmds = cmd.getCommandIterator().asScala.toList.reverse

        /**
         * Because `cmd.getCommandIterator()` returns a raw type and because the type
         * of the underlying instances belong to a inner private static Java class it
         * seems to be impossible to access it from Scala. Thus, the code is accessed
         * via Reflection.
         */
        for (c <- cmds) {
          val m = c.getClass().getMethod("execute", classOf[IDocument])
          m.setAccessible(true)
          m.invoke(c, doc)
        }
      case Remove(s) =>
        doc.replace(cmd.offset, cmd.length, "")
    }

    val offset = if (cmd.caretOffset > 0) cmd.caretOffset else cmd.offset + cmd.text.length
    doc.replace(offset, 0, "^")

    val expected = expectedOutput.replaceAll("\\$", "")
    val actual = doc.get()

    if (expected != actual) {
      throw new ComparisonFailure("", expected, actual)
    }
  }
}
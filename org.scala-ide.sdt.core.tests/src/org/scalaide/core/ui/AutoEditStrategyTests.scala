package org.scalaide.core.ui

import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.junit.Assert._
import org.junit.ComparisonFailure
import org.mockito.Mockito._
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.scalaide.core.internal.lexical.ScalaPartitions
import org.eclipse.jface.text.IDocumentExtension3

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

  val prefStore = mock(classOf[IPreferenceStore])

  def enable(property: String, enable: Boolean) {
    when(prefStore.getBoolean(property)).thenReturn(enable)
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
   */
  def test(input: String, expectedOutput: String, operation: Operation) {
    require(input.count(_ == '^') == 1, "the cursor in the input isn't set correctly")
    require(expectedOutput.count(_ == '^') == 1, "the cursor in the expected output isn't set correctly")

    def createDocument(input: String): IDocument = {
      val rawInput = input.filterNot(_ == '^')
      val doc = new Document(rawInput)
      val partitioner = new ScalaDocumentPartitioner

      doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
      doc.setDocumentPartitioner(IDocumentExtension3.DEFAULT_PARTITIONING, partitioner)
      partitioner.connect(doc)
      doc
    }

    val doc = createDocument(input)
    val rawInput = doc.get()
    val textOffset = input.indexOf('^')

    operation match {
      case Remove(declared) =>
        val actual = rawInput.substring(textOffset - declared.length, textOffset)
        require(declared == actual, "removeable content does not equal to the declared content")
      case _ =>
    }

    val cmd = operation match {
      case Add(s)    => new TestCommand(textOffset, 0, s, -1, true, true)
      case Remove(s) => new TestCommand(textOffset - s.length, s.length, "", -1, true, true)
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
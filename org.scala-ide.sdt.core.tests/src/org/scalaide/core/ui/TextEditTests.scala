package org.scalaide.core.ui

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension3
import org.junit.ComparisonFailure
import org.scalaide.CompilerSupportTests
import org.scalaide.core.lexical.ScalaCodePartitioner
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.util.eclipse.EclipseUtils

/**
 * This class provides basic test behavior for all text changing operations that
 * need to be tested.
 *
 * It provides the following DSL for a test:
 * {{{
 * class MyTextEditTest extends TextEditTests with EclipseDocumentSupport {
 *   case class MyOperation(value: String) extends Operation {
 *     def execute() = ???
 *   }
 *   @Test
 *   def test() = {
 *     "object A^" becomes "object AB^" after MyOperation("B")
 *   }
 * }
 * }}}
 *
 * The overall design of this test suite is as follows:
 *
 * - `TextEditTests` provides basic functionality and the DSL
 * - `TextEditTests` contains the class `Operation` which needs to be implemented.
 *   It provides the test logic that is executed by the test suite.
 * - Different tests require different test setups. By implementing the `prepare`
 *   method one can provide such a setup which is then invoked right before the
 *   `execute` method of the `Operation` class is called.
 * - The `execute` method of class `Operation` needs to change `caretOffset` in
 *   `TextEditTests` if the position of the cursor changes.
 */
abstract class TextEditTests {

  abstract class Operation {

    /** This value is initialized before the `execute` method is called. */
    var caretOffset: Int = _

    /**
     * Contains the test logic for a specific operation. This method is invoked
     * by the test suite.
     */
    def execute(): Unit

    /**
     * This function can handle Eclipse' linked mode model. To depict such a
     * model in the test simply surround the identifiers that should be
     * considered by the linked model with [[ and ]]. The cursor is always
     * represented by a ^.
     *
     * This function needs to be called by a concrete operation to add the [[
     * and ]] markers to `doc`. `cursorPos` is the position of the cursor where
     * the ^ marker should be added to `doc`. This function updates the cursor
     * position if necessary and returns its updated value. `groups` are pairs
     * of `(offset, length)` which span the area that should be surrounded
     * by the [[ and ]] markers.
     */
    def applyLinkedModel(doc: IDocument, cursorPos: Int, positionGroups: Seq[(Int, Int)]): Int = {
      val groups = positionGroups.sortBy(-_._1)
      val cursorOffset = groups.takeWhile(_._1 < cursorPos).size*4

      groups foreach {
        case (offset, length) =>
          doc.replace(offset+length, 0, "]]")
          doc.replace(offset, 0, "[[")
      }
      cursorPos+cursorOffset
    }
  }

  /** This method allows subclasses to provide their own test setup. */
  def runTest(source: String, operation: Operation): Unit

  /** This method allows the test suite to access the sources on which a test is executed. */
  def source: String

  final implicit class StringAsTest(input: String) {
    def becomes(expectedOutput: String) = input -> expectedOutput
  }
  final implicit class TestExecutor(testData: (String, String)) {
    def after(operation: Operation) = test(testData._1, testData._2, operation)
  }

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
  final def test(input: String, expectedOutput: String, operation: Operation): Unit = {
    require(input.count(_ == '^') == 1, "the cursor in the input isn't set correctly")
    require(expectedOutput.count(_ == '^') == 1, "the cursor in the expected output isn't set correctly")

    val inputWithoutDollarSigns = input.filterNot(_ == '$')
    val caretOffset = inputWithoutDollarSigns.indexOf('^')
    val inputWithoutCursor = inputWithoutDollarSigns.filterNot(_ == '^')

    operation.caretOffset = caretOffset
    runTest(inputWithoutCursor, operation)

    val expected = expectedOutput.replaceAll("\\$", "")
    val actual = new StringBuilder(source).insert(operation.caretOffset, "^").toString()

    if (expected != actual) {
      throw new ComparisonFailure("", expected, actual)
    }
  }
}

trait EclipseDocumentSupport {
  this: TextEditTests =>

  var doc: Document = _

  override def runTest(source: String, operation: Operation): Unit = {
    doc = new Document(source)
    val partitioner = ScalaCodePartitioner.documentPartitioner()

    doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
    doc.setDocumentPartitioner(IDocumentExtension3.DEFAULT_PARTITIONING, partitioner)
    partitioner.connect(doc)
    operation.execute()
  }

  override def source: String =
    doc.get()
}

trait CompilerSupport extends EclipseDocumentSupport with CompilerSupportTests {
  this: TextEditTests =>

  override def runTest(source: String, operation: Operation): Unit = {
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { _ =>
      super.runTest(source, operation)
    }
  }
}

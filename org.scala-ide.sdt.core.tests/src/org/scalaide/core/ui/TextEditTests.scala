package org.scalaide.core.ui

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocumentExtension3
import org.junit.AfterClass
import org.junit.ComparisonFailure
import org.scalaide.core.EclipseUserSimulator
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.junit.AfterClass

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
    val partitioner = new ScalaDocumentPartitioner

    doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
    doc.setDocumentPartitioner(IDocumentExtension3.DEFAULT_PARTITIONING, partitioner)
    partitioner.connect(doc)
    operation.execute()
  }

  override def source: String =
    doc.get()
}

trait CompilerSupport extends EclipseDocumentSupport {
  this: TextEditTests =>

  /** Can be overwritten in a subclass if desired. */
  val projectName: String = getClass().getSimpleName()

  private val project: ScalaProject = {
    val simulator = new EclipseUserSimulator
    simulator.createProjectInWorkspace(projectName)
  }

  override def runTest(source: String, operation: Operation): Unit = {
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { _ =>
      super.runTest(source, operation)
    }
  }

  def withCompiler(f: ScalaPresentationCompiler => Unit): Unit =
    project.presentationCompiler { compiler =>
      f(compiler)
    }

  /**
   * Creates a compilation unit whose underlying source file physically exists
   * in the test project of the test workspace. The file is placed in a unique
   * package name to prevent name clashes between generated files.
   *
   * The newly generated file is made available to the Eclipse platform and the
   * Scala compiler to allow the usage of the full non GUI feature set of the IDE.
   */
  final def mkCompilationUnit(source: String): ICompilationUnit = {
    val p = SDTTestUtils.createSourcePackage("testpackage" + System.nanoTime())(project)
    new EclipseUserSimulator().createCompilationUnit(p, "testfile.scala", source)
  }

  final def mkScalaCompilationUnit(source: String): ScalaCompilationUnit =
    mkCompilationUnit(source).asInstanceOf[ScalaCompilationUnit]

  @AfterClass
  final def deleteProject(): Unit = {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace()) { _ =>
      project.underlying.delete(/* force */ true, new NullProgressMonitor)
    }
  }
}
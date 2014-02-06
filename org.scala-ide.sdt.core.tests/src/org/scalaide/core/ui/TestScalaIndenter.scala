package scala.tools.eclipse.ui

import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.Test
import org.junit.Before

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceStore

import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner

import org.eclipse.jdt.internal.core.JavaCorePreferenceInitializer
import org.eclipse.jdt.ui.PreferenceConstants

import org.eclipse.ui.internal.editors.text.EditorsPlugin

import java.util.Hashtable

import org.eclipse.jface.text.TextUtilities

@RunWith(classOf[JUnit4ClassRunner])
class TestScalaIndenter {

  /**
   * Needed because the only constructor for DocumentCommand is protected
   */
  class InstantiableDocumentCommands extends DocumentCommand {

  }

  class MockPreferenceProvider extends PreferenceProvider {
    def updateCache : Unit = {
      // Nothing to do - we rely on someone setting up this cache
    }
  }

  /**
   * This class overrides several functions in ScalaAutoIndentStrategy to reduce
   * the overhead of things that need to be initialised for testing
   */
  class TestScalaAutoIndentStrategy(
      partitioning : String,
      project : IJavaProject,
      viewer : ISourceViewer,
      preferenceProvider : MockPreferenceProvider
    )  extends ScalaAutoIndentStrategy(partitioning, project, viewer, preferenceProvider) {
    override def computeSmartMode : Boolean = {
      return true
    }
  }

  val CARET = "_|_"

  @Before
  def initialiseClass() {
    // Initialisation fluff
    if (JavaPlugin.getDefault() == null) {
      new JavaPlugin()
    }
    if (EditorsPlugin.getDefault() == null) {
      new EditorsPlugin()
    }
    if (JavaCore.getJavaCore() == null) {
      new JavaCore()
    }
  }

  def runTest(textSoFar : String, insert : String, expectedResultWithCaret : String) {

    val document = new Document(textSoFar.replace(CARET, ""))

    // Create the command with all needed information
    val command = new InstantiableDocumentCommands()
    command.text = insert
    command.length = 0
    command.offset = textSoFar.indexOf(CARET)
    command.doit = true
    command.shiftsCaret = true
    command.caretOffset = -1

    // Preferences for indentation
    val preferenceProvider = new MockPreferenceProvider

    // For succinctness we alias DefaultCodeFormatterConstants to Dcfc
    import org.eclipse.jdt.core.formatter.{ DefaultCodeFormatterConstants => Dcfc }
    preferenceProvider.put(PreferenceConstants.EDITOR_CLOSE_BRACES, "true")
    preferenceProvider.put(PreferenceConstants.EDITOR_SMART_TAB, "true")
    preferenceProvider.put(Dcfc.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BLOCK, "true")
    preferenceProvider.put(Dcfc.FORMATTER_BRACE_POSITION_FOR_BLOCK, Dcfc.END_OF_LINE)
    preferenceProvider.put(Dcfc.FORMATTER_INDENT_BODY_DECLARATIONS_COMPARE_TO_TYPE_HEADER, "true")
    preferenceProvider.put(Dcfc.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION, Dcfc.END_OF_LINE)
    preferenceProvider.put(Dcfc.FORMATTER_TAB_CHAR, "space")
    preferenceProvider.put(ScalaIndenter.TAB_SIZE, "2")
    preferenceProvider.put(ScalaIndenter.INDENT_SIZE, "2")
    preferenceProvider.put(ScalaIndenter.INDENT_WITH_TABS, "false")

    val project = new JavaProject()

    val indentStrategy = new TestScalaAutoIndentStrategy(null, project, null, preferenceProvider)
    indentStrategy.customizeDocumentCommand(document, command)

    // Allow for not moving the offset
    val newOffset =
      if (command.caretOffset == -1) {
        (textSoFar.substring(0, textSoFar.indexOf(CARET)).replace(CARET, "") + command.text).length
      } else {
        command.caretOffset
      }

    val result =
      textSoFar.substring(0, textSoFar.indexOf(CARET)).replace(CARET, "") +
      command.text +
      textSoFar.substring(textSoFar.indexOf(CARET)).replace(CARET, "")

    // Replace system specific new lines in the result with a generic '\n'
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)
    val resultWithCleanNewLines = result.replaceAll(lineDelimiter, "\n")

    val expectedResult = expectedResultWithCaret.replace(CARET, "")
    assertEquals(expectedResult.replace(CARET, ""), resultWithCleanNewLines)

    val expectedOffset = expectedResultWithCaret.lastIndexOf(CARET) - CARET.length
    assertEquals(expectedOffset, newOffset)
  }


  /**
   * Test:
   *   class x {<->
   *
   * Becomes
   *   class x {
   *     <->
   *   }
   */
  @Test
  def testClassIndent() {

    val textSoFar =
      "class x {" + CARET

    val expectedResult =
      textSoFar + "\n" +
      "  " + CARET + "\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   trait x {<->
   *
   * Becomes
   *   trait x {
   *     <->
   *   }
   */
  @Test
  def testTraitIndent() {

    val textSoFar =
      "trait x {" + CARET

    val expectedResult =
      textSoFar + "\n" +
      "  " + CARET + "\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   class x {
   *     def y = {<->
   *   }
   *
   * Becomes
   *   class x {
   *     def y = {
   *       <->
   *     }
   *   }
   */
  @Test
  def testDefIndent() {
    val textSoFar =
      "class x {\n" +
      "  def y = {" + CARET + "\n" +
      "}"

    val expectedResult =
      "class x {\n" +
      "  def y = {" + CARET + "\n" +
      "    " + CARET + "\n" +
      "  }\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   class x {
   *     def y : Int = {<->
   *   }
   *
   * Becomes
   *   class x {
   *     def y : Int = {
   *       <->
   *     }
   *   }
   */
  @Test
  def defWithType() {
    val textSoFar =
      "class x {\n" +
      "  def y : Int = {" + CARET + "\n" +
      "}"

    val expectedResult =
      "class x {\n" +
      "  def y : Int = {" + CARET + "\n" +
      "    " + CARET + "\n" +
      "  }\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   class x {
   *     val xs = List[x]<->
   *   }
   *
   * Becomes
   *   class x {
   *     val xs = List[x]
   *     <->
   *   }
   */
  @Test
  def testGenericsIndent() {
    val textSoFar =
      "class x {\n" +
      "  val xs = List[x]" + CARET + "\n" +
      "}"

    val expectedResult =
      "class x {\n" +
      "  val xs = List[x]" + CARET + "\n" +
      "  " + CARET + "\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   class x {
   *     val xs = List[<->
   *   }
   *
   * Becomes
   *   class x {
   *     val xs = List[
   *         <->
   *   }
   */
  @Test
  def genericsIndentOverMultipleLines() {
    val textSoFar =
      "class x {\n" +
      "  val xs = List[" + CARET + "\n" +
      "}"
    val expectedResult =
      "class x {\n" +
      "  val xs = List[" + CARET + "\n" +
      "    " + CARET + "\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }


  /**
   * Test:
   *   class x {
   *     y()<->
   *   }
   *
   * Becomes
   *   class x {
   *     y()
   *     <->
   *   }
   */
  @Test
  def afterFunctionCall() {
    val textSoFar =
      "class x {\n" +
      "  y()" + CARET + "\n" +
      "}"

    val expectedResult =
      "class x {\n" +
      "  y()" + CARET + "\n" +
      "  " + CARET + "\n" +
      "}"

    runTest(textSoFar, "\n", expectedResult)
  }
}
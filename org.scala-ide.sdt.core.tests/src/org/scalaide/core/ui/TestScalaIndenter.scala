package org.scalaide.core.ui

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.formatter.{ DefaultCodeFormatterConstants => Dcfc }
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.internal.editors.text.EditorsPlugin
import org.junit.Assert._
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.runner.RunWith
import org.scalaide.core.lexical.ScalaCodePartitioner
import org.scalaide.ui.internal.editor.PreferenceProvider
import org.scalaide.ui.internal.editor.ScalaAutoIndentStrategy
import org.scalaide.ui.internal.editor.ScalaIndenter

@RunWith(classOf[JUnit4ClassRunner])
@Ignore("TODO - this test triggers an eclipse bundle that requires GUI support, which is not available on the build server")
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
  def initialiseClass(): Unit = {
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

  def runTest(textSoFar : String, insert : String, expectedResultWithCaret : String): Unit = {
    def nrOfCarets(str: String): Int = s"\\Q$CARET\\E".r.findAllIn(str).size

    val document = new Document(textSoFar.replace(CARET, ""))
    val partitioner = ScalaCodePartitioner.documentPartitioner()
    partitioner.connect(document)
    document.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)

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

    val indentStrategy = new TestScalaAutoIndentStrategy(IJavaPartitions.JAVA_PARTITIONING, project, null, preferenceProvider)
    indentStrategy.customizeDocumentCommand(document, command)

    // Allow for not moving the offset
    val newOffset =
      if (command.caretOffset == -1) {
        command.offset + command.text.length
      } else {
        command.caretOffset
      }

    val editedText = textSoFar.substring(0, command.offset) + command.text + textSoFar.substring(command.offset + command.length)
    val result = editedText.replaceAllLiterally(CARET, "")

    // Replace system specific new lines in the result with a generic '\n'
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)
    val resultWithCleanNewLines = result.replaceAll(lineDelimiter, "\n")

    val expectedResult = expectedResultWithCaret.replace(CARET, "")
    assertEquals(expectedResult.replace(CARET, ""), resultWithCleanNewLines)

    val delta = if (nrOfCarets(expectedResultWithCaret) > 1) CARET.length else 0
    val expectedOffset = expectedResultWithCaret.lastIndexOf(CARET) - delta
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
  def testClassIndent(): Unit = {

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
  def testTraitIndent(): Unit = {

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
  def testDefIndent(): Unit = {
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
  def defWithType(): Unit = {
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
  def testGenericsIndent(): Unit = {
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
  def genericsIndentOverMultipleLines(): Unit = {
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
  def afterFunctionCall(): Unit = {
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

  @Test
  def afterValDefNoRhs(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = $CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = $CARET
                             |    $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDef(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = "abc"$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = "abc"$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefEmptySpace(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = "abc"
                        |  $CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = "abc"
                             |  $CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefCharLit(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = 'a'$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = 'a'$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefCharLitEscaped(): Unit = {
    val textSoFar = raw"""|
                        |class x {
                        |  val x = '\n'$CARET
                        |}""".stripMargin

    val expectedResult = raw"""|
                             |class x {
                             |  val x = '\n'$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefCharLitEscapedBackSlash(): Unit = {
    val textSoFar = raw"""|
                        |class x {
                        |  val x = '\\'$CARET
                        |}""".stripMargin

    val expectedResult = raw"""|
                             |class x {
                             |  val x = '\\'$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefRawString(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = \"\"\"abcdef\"\"\"$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = \"\"\"abcdef\"\"\"$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefMiddleCaret(): Unit = {
    val textSoFar = s"""|
                        |class x {
                        |  val x = $CARET"abc"
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {
                             |  val x = $CARET
                             |    $CARET"abc"
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterValDefWithEscape(): Unit = {
    val textSoFar = s"""|
                        |class x {"
                        |  val x = "a\"bc"$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {"
                             |  val x = "a\"bc"$CARET
                             |  $CARET
                             |}""".stripMargin

    runTest(textSoFar, "\n", expectedResult)
  }

  @Test
  def afterIfElse(): Unit = {
    val textSoFar = s"""|
                        |class x {"
                        |  if (true)
                        |    1
                        |    els$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {"
                             |  if (true)
                             |    1
                             |  else$CARET
                             |}""".stripMargin
    runTest(textSoFar, "e", expectedResult)
  }

  @Test
  def afterIfElseNoChange(): Unit = {
    val textSoFar = s"""|
                        |class x {"
                        |  if (true)
                        |    1
                        |  els$CARET
                        |}""".stripMargin

    val expectedResult = s"""|
                             |class x {"
                             |  if (true)
                             |    1
                             |  else$CARET
                             |}""".stripMargin
    runTest(textSoFar, "e", expectedResult)
  }
}

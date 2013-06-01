package scala.tools.eclipse.ui

import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.ui.internal.editors.text.EditorsPlugin
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

object ScalaAutoIndentStrategyTest {

  val project = new JavaProject()

  val preferenceProvider = {

    val p = new PreferenceProvider {
      // Nothing to do - we rely on someone setting up this cache
      def updateCache() {}
    }

    import org.eclipse.jdt.core.formatter.{ DefaultCodeFormatterConstants => DCFC }

    for { (k, v) <- Seq(
      PreferenceConstants.EDITOR_CLOSE_BRACES -> "true",
      PreferenceConstants.EDITOR_SMART_TAB -> "true",
      DCFC.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BLOCK -> "true",
      DCFC.FORMATTER_BRACE_POSITION_FOR_BLOCK -> DCFC.END_OF_LINE,
      DCFC.FORMATTER_INDENT_BODY_DECLARATIONS_COMPARE_TO_TYPE_HEADER -> "true",
      DCFC.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION -> DCFC.END_OF_LINE,
      DCFC.FORMATTER_TAB_CHAR -> "space",
      ScalaIndenter.TAB_SIZE -> "2",
      ScalaIndenter.INDENT_SIZE -> "2",
      ScalaIndenter.INDENT_WITH_TABS -> "false")
    } p.put(k, v)

    p
  }

}

import ScalaAutoIndentStrategyTest._

class ScalaAutoIndentStrategyTest extends AutoEditStrategyTests(
    new ScalaAutoIndentStrategy(null, project, null, preferenceProvider) {
      override def computeSmartMode = true
    }) {

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

  @Test
  def testClassIndent() {
    val input = """
      class X {^
    """
    val expectedOutput = """
      class X {
        ^
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def testTraitIndent() {
    val input = """
      trait X {^
    """
    val expectedOutput = """
      trait X {
        ^
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def testDefIndent() {
    val input = """
      class X {
        def y = {^
      }
    """
    val expectedOutput = """
      class X {
        def y = {
          ^
        }
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def defWithType() {
    val input = """
      class X {
        def y: Int = {^
      }
    """
    val expectedOutput = """
      class X {
        def y: Int = {
          ^
        }
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def testGenericsIndent() {
    val input = """
      class X {
        val xs = List[X]^
      }
    """
    val expectedOutput = """
      class X {
        val xs = List[X]
        ^
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def genericsIndentOverMultipleLines() {
    val input = """
      class X {
        val xs = List[^
      }
    """
    val expectedOutput = """
      class X {
        val xs = List[
          ^
      }
    """
    test(input, expectedOutput, Add("\n"))
  }

  @Test
  def afterFunctionCall() {
    val input = """
      class X {
        y()^
      }
    """
    val expectedOutput = """
      class X {
        y()
        ^
      }
    """
    test(input, expectedOutput, Add("\n"))
  }
}
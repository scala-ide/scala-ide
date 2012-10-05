package scala.tools.eclipse.ui

import org.eclipse.jdt.internal.core.util.SimpleDocument
import org.junit.Test
import AutoEditStrategyTests._
import org.eclipse.jface.text.Document
import scala.tools.eclipse.lexical.ScalaDocumentPartitioner
import org.eclipse.jface.text.IDocument
import org.eclipse.jdt.ui.text.IJavaPartitions

class ScaladocAutoEditStrategyTest {

  val strategy = new ScaladocAutoIndentStrategy(IJavaPartitions.JAVA_PARTITIONING)
  
  @Test
  def openDocComment_topLevel() {
    val input =
      """
/**^
class Foo
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * \n */", cmd.offset + 4, false, true, cmd)
  }

  @Test
  def openDocComment_topLevel_with_nested() {
    val input =
      """
/**^
class Foo {
   /** blah */
   def foo() {}
}
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * \n */", cmd.offset + 4, false, true, cmd)
  }

  @Test
  def openDocComment_topLevel_with_stringLit() {
    val input =
      """
/**^
class Foo {
   def foo() {
        "/* */" // tricky, this trips the Java auto-edit :-D
  }
}
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * \n */", cmd.offset + 4, false, true, cmd)
  }

  @Test
  def openDocComment_nested() {
    val input =
      """
/** blah */
class Foo {
   /**^
   def foo() {
  }
}
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n    * \n    */", cmd.offset + 7, false, true, cmd)
  }

  @Test
  def openDocComment_nested_with_other_docs() {
    val input =
      """
/** blah */
class Foo {
   /**^
   def foo() {
  }
  /** */
  def bar
}
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n    * \n    */", cmd.offset + 7, false, true, cmd)
  }

  @Test
  def closedDocComment_topLevel() {
    val input =
      """
/** ^blah */
class Foo {
}
"""
    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_topLevel_nested() {
    val input =
      """
/** blah */
class Foo {
   /**^*/
   def foo() {
  }
  /** */
  def bar
}
"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n    * ", -1, false, true, cmd)
  }

  @Test
  def openDocComment_at_beginning() {
    val input =
      """/**^class Foo {
   def foo() {
  }
  /** */
  def bar
}
"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * \n */", cmd.offset + 4, false, true, cmd)
  }

  @Test
  def openDocComment_at_end() {
    val input =
      """
class Foo {
}/**^"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n* ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_first_char_of_line() {
    val input =
      """
/**
^
*/
class Foo {
}"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n* ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_line_break() {
    val input =
      """
/** one^two
 */
class Foo {
}"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n * ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_line_break_nested() {
    val input =
      """
class Foo {
  /** one^two
   */
  def meth() {}
      
}"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n   * ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_nop_end() {
    val input =
      """
class Foo {
  /** one two *^/
  def meth() {}
      
}"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n   * ", -1, false, true, cmd)
  }

  @Test
  def closedDocComment_nop_beginning() {
    val input =
      """
class Foo {
  /^** one two */
  def meth() {}
      
}"""

    val cmd = testCommand(input)
    strategy.customizeDocumentCommand(testDocument(input), cmd)
    checkCommand(cmd.offset, 0, "\n   * ", -1, false, true, cmd)
  }

  def testDocument(input: String): IDocument = {
    val rawInput = input.filterNot(_ == '^')
    val doc = new Document(rawInput)
    val partitioner = new ScalaDocumentPartitioner
    doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
    partitioner.connect(doc)
    doc
  }

  def testCommand(input: String): TestCommand = {
    val pos = input.indexOf('^')
    new TestCommand(pos, 0, "\n", -1, false, true)
  }
}
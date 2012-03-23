package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class MethodTest extends AbstractSymbolClassifierTest {

  @Test
  def method() {
    checkSymbolClassification("""
      object A {
        def method(): Int = method
      }""", """
      object A {
        def $METH$(): Int = $METH$
      }""",
      Map("METH" -> Method))
  }

  @Test
  def method_with_backticks() {
    checkSymbolClassification("""
      object A {
        {
          def `method` = 42
          `method` 
        }
      }""", """
      object A {
        {
          def $ METH $ = 42
          $ METH $ 
        }
      }""",
      Map("METH" -> Method))
  }

  @Test
  def bug_with_backticks() {
    checkSymbolClassification("""
      object A {
        List.`apply`(42)
      }""", """
      object A {
        List.$METH $(42)
      }""",
      Map("METH" -> Method))
  }

  @Test
  def import_method() {
    checkSymbolClassification("""
      import System.currentTimeMillis
      """, """
      import System.$     METH      $
      """,
      Map("METH" -> Method))
  }

  @Test
  def classOf() {
    checkSymbolClassification("""
      class A { classOf[A] }
      """, """
      class A { $METH $[A] }
      """,
      Map("METH" -> Method))
  }

  @Test
  @Ignore
  def import_renaming() {
    checkSymbolClassification("""
      import System.{ currentTimeMillis => bobble }
      class A {
        bobble
      }""", """
      import System.{ $     METH      $ => $METH$ }
      class A {
        $METH$
      }""",
      Map("METH" -> Method))
  }
}
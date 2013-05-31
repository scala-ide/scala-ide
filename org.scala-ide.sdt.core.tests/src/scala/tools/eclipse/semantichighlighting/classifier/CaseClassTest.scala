package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class CaseClassTest extends AbstractSymbolClassifierTest {

  @Test
  def case_class() {
    checkSymbolClassification("""
      case class CaseClass {
        def method(other: CaseClass) = 42
      }""", """
      case class $CASECLS$ {
        def method(other: $CASECLS$) = 42
      }""",
      Map("CASECLS" -> CaseClass))
  }

  @Test
  def case_class_creation() {
    checkSymbolClassification("""
      case class CaseClass(n: Int) {
        CaseClass(42)
      }""", """
      case class $CASECLS$(n: Int) {
        $CASECLS$(42)
      }""",
      Map("CASECLS" -> CaseClass))
  }

  @Test
  def case_class_in_import() {
    checkSymbolClassification("""
      package foo { case class CaseClass }
      import foo.CaseClass
      """, """
      package foo { case class $CASECLS$ }
      import foo.$CASECLS$
      """,
      Map("CASECLS" -> CaseClass))
  }

  @Test
  def case_class_pattern_match() {
    checkSymbolClassification("""
      object X {
        val Some(x) = Some(42)
      }""", """
      object X {
        val $CC$(x) = Some(42)
      }""",
      Map("CC" -> CaseClass))
  }

  @Test
  @Ignore("Enable when ticket #1001171 is fixed")
  def infix_notation_for_extractors() {
    checkSymbolClassification("""
        class X {
          val a Foo b = Foo(1, 2)
        }
        case class Foo(a: Int, b: Int)
        """, """
        class X {
          val a $C$ b = $C$(1, 2)
        }
        case class $C$(a: Int, b: Int)
        """,
        Map("C" -> CaseClass))
  }

}
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

}
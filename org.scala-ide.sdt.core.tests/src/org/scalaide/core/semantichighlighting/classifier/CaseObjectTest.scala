package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class CaseObjectTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_case_object() {
    checkSymbolClassification("""
      case object CaseObject { CaseObject }
      """, """
      case object $CASEOBJ $ { $CASEOBJ $ }
      """,
      Map("CASEOBJ" -> CaseObject))
  }

  @Test
  def pattern_match() {
    checkSymbolClassification("""
      object X {
        Option(42) match {
          case Some(x) => 42
          case None => 24
        }
      }""", """
      object X {
        Option(42) match {
          case $CC$(x) => 42
          case $CO$ => 24
        }
      }""",
      Map(
        "CO" -> CaseObject,
        "CC" -> CaseClass))
  }

  @Test
  def import_case_object() {
    checkSymbolClassification("""
      import scala.None
      class A { None }
        """, """
      import scala.$CO$
        """,
      Map("CO" -> CaseObject))
  }

}
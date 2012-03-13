package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class TypeTest extends AbstractSymbolClassifierTest {

  @Test
  def type_alias_in_extends_clause() {
    checkSymbolClassification("""
      class Bob extends RuntimeException("foo")
      """, """
      class Bob extends $      TYPE    $("foo")
      """,
      Map("TYPE" -> Type))
  }

  @Test
  def import_type() {
    checkSymbolClassification("""
        object X { type Type = Int }
        object Y { import X.Type }
      """, """
        object X { type $TP$ = Int }
        object Y { import X.$TP$ }
      """,
      Map("TP" -> Type))
  }

  @Ignore
  @Test
  def set_is_a_type() {
    checkSymbolClassification("""
      case class Bob(s: Set[Int])
      object X {
        val Bob(s) = Bob(Set())
      }
      """, """
      case class Bob(s: $T$[Int])
      object X {
        val Bob(s) = Bob($V$())
      }
      """,
      Map("T" -> Type, "V" -> TemplateVal))
  }

}
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

  @Test
  def path_dependent_type() {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: MTrait)(k: m.KTrait[Int])
      }
      """, """
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: $TT  $)(k: m.$TT  $[$C$])
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  def type_projection() {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: MTrait#KTrait[Int])
      }
      """, """
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: $TT  $#$TT  $[$C$])
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  @Ignore("Enable when ticket #1001239 is fixed")
  def deep_type_projection() {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] { trait HTrait } }
      trait X {
        def xs(m: MTrait#KTrait[Int]#HTrait)
      }
      """, """
      trait MTrait { trait KTrait[A] { trait HTrait } }
      trait X {
        def xs(m: $TT  $#$TT  $[$C$]#$TT  $)
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  @Ignore("Enable when ticket #1001046 is fixed")
  def classify_type_in_abstract_val() {
    checkSymbolClassification("""
      trait X {
        val s: String
      }
      """, """
      trait X {
        val s: $TPE $
      }
      """,
      Map("TPE" -> Type))
  }

}
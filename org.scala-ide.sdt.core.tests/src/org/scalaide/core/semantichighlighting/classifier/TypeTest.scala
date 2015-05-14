package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class TypeTest extends AbstractSymbolClassifierTest {

  @Test
  def type_alias_in_extends_clause(): Unit = {
    checkSymbolClassification("""
      class Bob extends RuntimeException("foo")
      """, """
      class Bob extends $      TYPE    $("foo")
      """,
      Map("TYPE" -> Type))
  }

  @Test
  def import_type(): Unit = {
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
  def path_dependent_type(): Unit = {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: MTrait)(k: m.KTrait[Int]): Unit
      }
      """, """
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: $TT  $)(k: m.$TT  $[$C$]): Unit
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  def type_projection(): Unit = {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: MTrait#KTrait[Int]): Unit
      }
      """, """
      trait MTrait { trait KTrait[A] }
      trait X {
        def xs(m: $TT  $#$TT  $[$C$]): Unit
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  @Ignore("Enable when ticket #1001239 is fixed")
  def deep_type_projection(): Unit = {
    checkSymbolClassification("""
      trait MTrait { trait KTrait[A] { trait HTrait } }
      trait X {
        def xs(m: MTrait#KTrait[Int]#HTrait): Unit
      }
      """, """
      trait MTrait { trait KTrait[A] { trait HTrait } }
      trait X {
        def xs(m: $TT  $#$TT  $[$C$]#$TT  $): Unit
      }
      """,
      Map("C" -> Class, "TT" -> Trait))
  }

  @Test
  @Ignore("Enable when ticket #1001046 is fixed")
  def classify_type_in_abstract_val(): Unit = {
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

  @Test
  def singleton_type(): Unit = {
    checkSymbolClassification("""
      object X {
        def s: Obj.type = null
      }
      object Obj
      """, """
      object X {
        def s: $O$.type = null
      }
      object Obj
      """,
      Map("O" -> Object))
  }

}
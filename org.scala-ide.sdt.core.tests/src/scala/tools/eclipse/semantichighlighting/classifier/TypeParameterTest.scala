package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class TypeParameterTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_type_param() {
    checkSymbolClassification("""
      trait C[TypeParam] {
        def x: TypeParam
      }""", """
      trait C[$TPARAM $] {
        def x: $TPARAM $
      }""",
      Map("TPARAM" -> TypeParameter))
  }
  
  @Test
  def method_type_param() {
    checkSymbolClassification("""
      trait X {
        def x[TypeParam]: TypeParam
      }""", """
      trait X {
        def x[$TPARAM $]: $TPARAM $
      }""",
      Map("TPARAM" -> TypeParameter))
  }

  @Test
  def parameterized_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Seq[TypeParam]
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$TPARAM $]
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }

  @Test
  def nested_parameterized_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Seq[Seq[TypeParam]]
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$T$[$TPARAM $]]
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }

  @Test
  def multiple_parameterized_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Map[TypeParam, Seq[TypeParam]]
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$TPARAM $, $T$[$TPARAM $]]
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }

  @Test
  def partial_compound_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Seq[TypeParam] with collection.IterableLike[TypeParam, TypeParam]
      }
      """, """
      trait X {
        def xs[$TPARAM $]: Seq[$TPARAM $] with collection.IterableLike[$TPARAM $, $TPARAM $]
      }
      """,
      Map("TPARAM" -> TypeParameter))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST")
  def full_compound_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Seq[TypeParam] with collection.IterableLike[TypeParam, TypeParam]
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$TPARAM $] with collection.$TRAIT     $[$TPARAM $, $TPARAM $]
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type, "TRAIT" -> Trait))
  }
}
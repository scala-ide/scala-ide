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

  @Test
  def context_bound_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[A : Ordering](a: A)
        def ys[TypeParam](a: TypeParam)(implicit evidence$1: Ordering[TypeParam])
      }
      """, """
      trait X {
        def xs[A : $TYPE  $](a: A)
        def ys[$TPARAM $](a: $TPARAM $)(implicit evidence\$1: $TYPE  $[$TPARAM $])
      }
      """,
      Map("TPARAM" -> TypeParameter, "TYPE" -> Type))
  }

  @Test
  def function_literal_type_param() {
    checkSymbolClassification("""
      trait X {
        def f: Int => String
        def g: Function1[Int, String]
      }
      """, """
      trait X {
        def f: $C$ => $TYPE$
        def g: $TRAIT  $[$C$, $TYPE$]
      }
      """,
      Map("TYPE" -> Type, "C" -> Class, "TRAIT" -> Trait))
  }

  @Test
  def tuple_literal_type_param() {
    checkSymbolClassification("""
      trait X {
        def f: (Int, String)
        def g: Tuple2[Int, String]
      }
      """, """
      trait X {
        def f: ($C$, $TYPE$)
        def g: $CC  $[$C$, $TYPE$]
      }
      """,
      Map("TYPE" -> Type, "C" -> Class, "CC" -> CaseClass))
  }

  @Test
  def partial_structural_type_param() {
    checkSymbolClassification("""
      trait X {
        def f(s: { def foo[TypeParam](i: TypeParam): Int })
      }
      """, """
      trait X {
        def f(s: { def $M$[TypeParam](i: TypeParam): Int })
      }
      """,
      Map("M" -> Method))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST")
  def full_structural_type_param() {
    checkSymbolClassification("""
      trait X {
        def f(s: { def foo[TypeParam](i: TypeParam): Int })
      }
      """, """
      trait X {
        def f(s: { def $M$[$TPARAM $](i: $TPARAM $): $C$ })
      }
      """,
      Map("TPARAM" -> TypeParameter, "C" -> Class, "M" -> Method))
  }

  @Test
  def bounded_type_param() {
    checkSymbolClassification("""
      trait X {
        type Type[TypeParam] >: List[TypeParam] <: Iterable[TypeParam]
      }
      """, """
      trait X {
        type $T $[$TPARAM $] >: $T $[$TPARAM $] <: $T     $[$TPARAM $]
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }

  @Test
  def view_bound_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam <% Ordering[TypeParam]](a: TypeParam)
        def ys[TypeParam](a: TypeParam)(implicit evidence$1: TypeParam => Ordering[TypeParam])
      }
      """, """
      trait X {
        def xs[$TPARAM $ <% $TYPE  $[$TPARAM $]](a: $TPARAM $)
        def ys[$TPARAM $](a: $TPARAM $)(implicit evidence\$1: $TPARAM $ => $TYPE  $[$TPARAM $])
      }
      """,
      Map("TPARAM" -> TypeParameter, "TYPE" -> Type))
  }

  @Test
  def higher_kinded_type_param() {
    checkSymbolClassification("""
      trait M[A[_]]
      trait H extends M[List]
      """, """
      trait M[A[_]]
      trait H extends M[$HK$]
      """,
      Map("HK" -> Type))
  }

  @Test
  def partial_existential_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Res[TypeParam] forSome { type Res[_] <: Seq[_] }
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$TPARAM $] forSome { type $T$[_] <: Seq[_] }
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST")
  def full_existential_type_param() {
    checkSymbolClassification("""
      trait X {
        def xs[TypeParam]: Res[TypeParam] forSome { type Res[_] <: Seq[_] }
      }
      """, """
      trait X {
        def xs[$TPARAM $]: $T$[$TPARAM $] forSome { type $T$[_] <: $T$[_] }
      }
      """,
      Map("TPARAM" -> TypeParameter, "T" -> Type))
  }
}
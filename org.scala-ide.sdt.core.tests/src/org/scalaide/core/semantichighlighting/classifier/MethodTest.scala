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
  @Ignore("The renamed method doesn't have a symbol and the current classification strategy needs the symbol's name.")
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

  @Test
  def test_synthetic_function_param() {
    checkSymbolClassification("""
      object A {
        {
          def addOne(x: Int): Int = x + 1
          List(3) map addOne
        }
      }""", """
      object A {
        {
          def addOne(x: Int): Int = x + 1
          List(3) map $METH$
        }
      }""",
      Map("METH" -> Method))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001223)")
  def param_of_classOf() {
    checkSymbolClassification("""
      object X {
        val x = classOf[Int]
      }
      """, """
      object X {
        val x = classOf[$T$]
      }
      """,
      Map("T" -> Type))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001228)")
  def combination_of_implicit_conversion_and_higher_order_method_call() {
    checkSymbolClassification("""
      object X {
        val s: String = Seq(1).map(param=>param)
        implicit def l2s(i: Seq[Int]): String = i.mkString
      }
      """, """
      object X {
        val s: String = $O$(1).$M$($P  $=>$P  $)
        implicit def l2s(i: Seq[Int]): String = i.mkString
      }
      """,
      Map("O" -> Object, "M" -> Method, "P" -> Param))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001242)")
  def internal_notation_of_operator_names() {
    checkSymbolClassification("""
      object X {
        val xs = Nil $colon$colon 0
      }
      """, """
      object X {
        val xs = Nil $METHOD    $ 0
      }
      """,
      Map("METHOD" -> Method))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001259)")
  def param_of_super() {
    checkSymbolClassification("""
      object X {
        val bool = super[Object].equals(this)
      }
      """, """
      object X {
        val bool = super[$CLS $].equals(this)
      }
      """,
      Map("CLS" -> Class))
  }

  @Test(expected = Predef.classOf[AssertionError])
  def while_keyword_is_not_treated_as_method() {
    checkSymbolClassification("""
      object X {
        while (false) {}
      }
      """, """
      object X {
        $MET$ (false) {}
      }
      """,
      Map("MET" -> Method))
  }
}
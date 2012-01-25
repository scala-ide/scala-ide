package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class LocalVarTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_var_decl() {
    checkSymbolClassification("""
      object A {
        {
           var xxxxxx = 100
        }
      }""", """
      object A {
        {
           var $LVAR$ = 100
        }
      }""",
      Map("LVAR" -> LocalVar))
  }

  @Test
  def basic_var_decl_and_ref() {
    checkSymbolClassification("""
      object A {
        {
           var xxxxxx = 100
           xxxxxx = xxxxxx + 1
        }
      }""", """
      object A {
        {
           var $LVAR$ = 100
           $LVAR$ = $LVAR$ + 1
        }
      }""",
      Map("LVAR" -> LocalVar))
  }

  @Test
  def increment() {
    checkSymbolClassification("""
      object A {
        {
           var xxxxxx = 100
           xxxxxx += 1
        }
      }""", """
      object A {
        {
           var $LVAR$ = 100
           $LVAR$ += 1
        }
      }""",
      Map("LVAR" -> LocalVar))
  }

  @Test
  def var_defined_in_pattern() {
    checkSymbolClassification("""
      object A {
        {
           var Some(xxxxxx) = Some(42)
           xxxxxx += 1
        }
      }""", """
      object A {
        {
           var Some($LVAR$) = Some(42)
           $LVAR$ += 1
        }
      }""",
      Map("LVAR" -> LocalVar))
  }

  @Test
  def vars_defined_in_pattern() {
    checkSymbolClassification("""
      object A {
        {
          var List(xxxxxxx, yyyyyyy) = List(1, 2)
        }
      }""", """
      object A {
        {
          var List($LVAR1$, $LVAR2$) = List(1, 2)
        }
      }""",
      Map("LVAR1" -> LocalVar, "LVAR2" -> LocalVar))
  }

  @Test
  def assignment_is_not_classified_as_a_named_argument {
    checkSymbolClassification("""
object A {
  {
    def meth2(y: Unit) = 42
    var xxxxxx = 10
    meth2(xxxxxx = 20)
  }
}
""", """
object A {
  {
    def meth2(y: Unit) = 42
    var $LVAR$ = 10
    meth2($LVAR$ = 20)
  }
}
""",
      Map("LVAR" -> LocalVar))
  }

}
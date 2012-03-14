package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class TemplateVarTest extends AbstractSymbolClassifierTest {

  @Test
  def method {
    checkSymbolClassification("""
      object A {
        var xxxxxx = 12
        xxxxxx
        xxxxxx += 1
        xxxxxx = xxxxxx + 1
      }""", """
      object A {
        var $TVAR$ = 12
        $TVAR$
        $TVAR$ += 1
        $TVAR$ = $TVAR$ + 1
      }""",
      Map("TVAR" -> TemplateVar))
  }

  @Test
  def class_params() {
    checkSymbolClassification("""
      class B(var xxxxxx: String)
      """, """
      class B(var $TVAR$: String)
      """,
      Map("TVAR" -> TemplateVar))
  }

  @Test
  def var_class_params_passed_into_super_constructor() {
    checkSymbolClassification("""
      class A(var xxxxxx: String) extends RuntimeException(xxxxxx)
      """, """
      class A(var $TVAR$: String) extends RuntimeException($TVAR$)
      """,
      Map("TVAR" -> TemplateVar))
  }
   
  @Test
  def import_template_var() {
    checkSymbolClassification("""
      object A { var xxxxxx: String = _ }
      object B { import A.xxxxxx }
        """, """
      object A { var $TVAR$: String = _ }
      object B { import A.$TVAR$ }
        """,
      Map("TVAR" -> TemplateVar))
  }
 
}
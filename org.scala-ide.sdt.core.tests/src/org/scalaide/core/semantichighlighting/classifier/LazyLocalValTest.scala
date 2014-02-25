package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class LazyLocalValTest extends AbstractSymbolClassifierTest {

  @Test
  def decl_and_ref_of_lazy_vals() {
    checkSymbolClassification("""
      object A {
        {
           lazy val xxxxxx = 100
           xxxxxx * xxxxxx
           lazy val `xxxx` = 100
           `xxxx` * `xxxx`
        }
      }""", """
      object A {
        {
           lazy val $LVAL$ = 100
           $LVAL$ * $LVAL$
           lazy val $LVAL$ = 100
           $LVAL$ * $LVAL$
        }
      }""",
      Map("LVAL" -> LazyLocalVal))
  }

  @Test
  def lazy_val_in_pattern_val_def() {
    checkSymbolClassification("""
        class A {{
          lazy val Some(immutableVal) = Some(42)
          immutableVal
        }}""", """
        class A {{
          lazy val Some($   TVAL   $) = Some(42)
          $   TVAL   $
        }}""",
      Map("TVAL" -> LazyLocalVal))
  }

}
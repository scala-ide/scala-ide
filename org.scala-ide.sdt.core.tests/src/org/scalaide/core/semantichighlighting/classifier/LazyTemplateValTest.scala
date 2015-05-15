package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class LazyTemplateValTest extends AbstractSymbolClassifierTest {

  @Test
  def lazy_template_val(): Unit = {
    checkSymbolClassification("""
        class A {
          lazy val immutableVal = 42
          immutableVal
        }""", """
        class A {
          lazy val $   TVAL   $ = 42
          $   TVAL   $
        }""",
      Map("TVAL" -> LazyTemplateVal))
  }

  @Test
  def lazy_template_val_in_pattern_val_def(): Unit = {
    checkSymbolClassification("""
        class A {
          lazy val Some(immutableVal) = Some(42)
          immutableVal
        }""", """
        class A {
          lazy val Some($   TVAL   $) = Some(42)
          $   TVAL   $
        }""",
      Map("TVAL" -> LazyTemplateVal))
  }

  @Test
  def import_lazy_template_val(): Unit = {
    checkSymbolClassification("""
      object A { lazy val xxxxxx: Int = 42 }
      object B { import A.xxxxxx }
        """, """
      object A { lazy val $TVAL$: Int = 42 }
      object B { import A.$TVAL$ }
        """,
      Map("TVAL" -> LazyTemplateVal))
  }

}
package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class TraitTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_trait() {
    checkSymbolClassification("""
      trait Trait
      trait `Trait2` {
         new Trait {}
         new `Trait2` {}
      }""", """
      trait $TRT$
      trait $ TRT  $ {
         new $TRT$ {}
         new $ TRT  $ {}
      }""",
      Map("TRT" -> Trait))
  }

  @Test
  def import_scala_trait() {
    checkSymbolClassification("""
      import scala.concurrent.Promise
      """, """
      import scala.concurrent.$TRAIT$
      """,
      Map("TRAIT" -> Trait))
  }

  @Test
  def an_imported_java_interface_is_classified_as_a_trait() {
    checkSymbolClassification("""
      import java.lang.Runnable
      """, """
      import java.lang.$TRAIT $
      """,
      Map("TRAIT" -> Trait))
  }

  @Test
  @Ignore("Enable when ticket #1001176 is fixed")
  def imported_self_reference_is_classified_as_trait() {
    checkSymbolClassification("""
        package ab {
          trait TheTrait
        }
        package cd {
          trait K { self: ab.TheTrait => }
          import ab.TheTrait
          trait M { self: TheTrait => }
        }
        """, """
        package ab {
          trait $TRAIT $
        }
        package cd {
          trait K { self: ab.$TRAIT $ => }
          import ab.$TRAIT $
          trait M { self: $TRAIT $ => }
        }
        """,
        Map("TRAIT" -> Trait))
  }
}
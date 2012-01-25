package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class TraitTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_trait {
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
  def import_scala_trait {
    checkSymbolClassification("""
      import scala.concurrent.ManagedBlocker
      """, """
      import scala.concurrent.$   TRAIT    $
      """,
      Map("TRAIT" -> Trait))
  }

  @Test
  def an_imported_java_interface_is_classified_as_a_trait {
    checkSymbolClassification("""
      import java.lang.Runnable
      """, """
      import java.lang.$TRAIT $
      """,
      Map("TRAIT" -> Trait))
  }

}
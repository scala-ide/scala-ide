package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class ObjectTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_object() {
    checkSymbolClassification("""
      object Object
      object `Object2` {
         Object
         `Object`
      }""", """
      object $OBJ $
      object $  OBJ  $ {
         $OBJ $
         $  OBJ $
      }""",
      Map("OBJ" -> Object))
  }

  @Test
  @Ignore
  def package_object() {
    checkSymbolClassification("""
      package object packageObject {
        val x = 42
        packageObject.x
      }""", """
      package object $   OBJECT  $ {
        val x = 42
        $   OBJECT  $.x
      }""",
      Map("OBJECT" -> Object))
  }

  @Test
  def import_object() {
    checkSymbolClassification("""
      package pack { object Object }
      import pack.Object
    """, """
      package pack { object $OBJ $ }
      import pack.$OBJ $
    """,
      Map("OBJ" -> Object))
  }
  
}
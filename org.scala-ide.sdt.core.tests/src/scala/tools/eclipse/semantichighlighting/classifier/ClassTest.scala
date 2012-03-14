package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class ClassTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_class() {
    checkSymbolClassification("""
      class Class
      class `Class2` {
         new Class
         new `Class2`
      }""", """
      class $CLS$
      class $ CLS  $ {
         new $CLS$
         new $ CLS  $
      }""",
      Map("CLS" -> Class))
  }

  @Test
  @Ignore
  def inside_class_of_expressions() {
    checkSymbolClassification("""
      class Class {
        classOf[Class]
      }""", """
      class $CLS$ {
        classOf[$CLS$]
      }""",
      Map("CLS" -> Class))
  }

  @Test
  @Ignore
  def problem_with_import_and_existential_type() {
    checkSymbolClassification("""
      trait X {
        import java.lang.Class
        val c: Class[_]
      }""", """
      trait X {
        import java.lang.$CLS$
        val c: $CLS$[_]
      }""",
      Map("CLS" -> Class))
  }

  @Test
  @Ignore
  def problem_with_java_import() {
    checkSymbolClassification("""
      import java.util.Properties
     """, """
      import java.util.$ CLASS  $
     """,
      Map("CLASS" -> Class))
  }

  @Test
  def static_java_ref() {
    checkSymbolClassification("""
      object Object {
        Integer.parseInt("42")
      }""", """
      object Object {
        $CLASS$.parseInt("42")
      }""",
      Map("CLASS" -> Class))
  }

  @Test
  def import_symbol_which_is_both_a_class_and_an_object() {
    checkSymbolClassification("""
      import scala.collection.immutable.List
        """, """
      import scala.collection.immutable.$CL$
        """,
      Map("CL" -> Class))
  }
  
}
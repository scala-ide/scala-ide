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
  def java_import() {
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
  def import_a_class() {
    checkSymbolClassification("""
      import scala.collection.generic.GenMapFactory
        """, """
      import scala.collection.generic.$   CL      $
        """,
      Map("CL" -> Class))
  }

  @Test
  @Ignore("Packages' symbol seem to take a TransparentPosition, why?")
  def import_a_renamed_class() {
    checkSymbolClassification("""
      import scala.concurrent.{Lock => LOCK}

      object Foo {
        var lock: LOCK = _
      }
        """, """
      import $PKG$.$   PKG  $.{$CL$ => LOCK}

      object Foo {
        var lock: LOCK = _
      }
        """,
      Map("CL" -> Class, "PKG" -> Package))
  }

  @Test
  @Ignore("The renamed class doesn't have a symbol and the current classification strategy needs the symbol's name.")
  def import_a_renamed_class_and_color_it() {
    checkSymbolClassification("""
      import scala.concurrent.{ Lock => Alock }

      object Foo {
        def foo( f: Alock ) {}
      }
        """, """
      import $PKG$.$   PKG  $.{ $C1$ => $ C2$ }

      object Foo {
        def foo( f: $ C2$ ) {}
      }
        """,
      Map("C1" -> Class, "PKG" -> Package, "C2" -> Class))
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

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001260)")
  def early_initializer_in_combination_with_trait() {
    checkSymbolClassification("""
      object X extends {
        val o = new Object
      } with T
      trait T
      """, """
      object X extends {
        val o = new $CLS $
      } with T
      trait T
      """,
      Map("CLS" -> Class))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001334)")
  def default_arguments() {
    checkSymbolClassification("""
      class Foo(val f: Int = Bar.value)
      object Bar {
        val value = 0
      }
      """, """
      class Foo(val f: Int = $C$.$VAL$)
      object Bar {
        val value = 0
      }
      """,
      Map("C" -> Class, "VAL" -> TemplateVal))
  }
}
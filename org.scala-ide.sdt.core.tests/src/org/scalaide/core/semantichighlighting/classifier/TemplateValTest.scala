package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class TemplateValTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_template_val(): Unit = {
    checkSymbolClassification("""
      object A {
        val xxxxxx = 12
      }""", """
      object A {
        val $TVAL$ = 12
      }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def set_in_predef_is_a_val(): Unit = {
    checkSymbolClassification("""
      object A {
        Set(1, 2, 3)
      }""", """
      object A {
        $T$(1, 2, 3)
      }""",
      Map("T" -> TemplateVal))
  }


  @Test
  def backticked_identifiers(): Unit = {
    checkSymbolClassification("""
      class A {
        val `identifier` = 42
        `identifier`
      }""", """
      class A {
        val $   TVAL   $ = 42
        $   TVAL   $
      }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def vals_from_objects(): Unit = {
    checkSymbolClassification("""
      object X { val objectMember = 42 }
      class A { X.objectMember }""", """
      object X { val $    TVAL  $ = 42 }
      class A { X.$    TVAL  $ }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def self_references_are_classified_as_template_vals(): Unit = {
    checkSymbolClassification("""
      class A { self =>
        self
        def self(n: Int) = 42
      }""", """
      class A { $TV$ =>
        $TV$
        def $MD$(n: Int) = 42
      }""",
      Map("TV" -> TemplateVal, "MD" -> Method))
  }

  @Test
  def case_class_params(): Unit = {
    checkSymbolClassification("""
      case class A(xxxxxx: String)
      """, """
      case class A($TVAL$: String)
      """,
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def class_params(): Unit = {
    checkSymbolClassification("""
      class A(xxxxxxx: String)
      class B(val xxxxxx: String)
      """, """
      class A($PARAM$: String)
      class B(val $TVAL$: String)
      """,
      Map("PARAM" -> Param, "TVAL" -> TemplateVal))
  }

  @Test
  def class_params_passed_into_super_constructor(): Unit = {
    checkSymbolClassification("""
      class A(xxxxxx: String) extends RuntimeException(xxxxxx)
      """, """
      class A($PARA$: String) extends RuntimeException($PARA$)
      """,
      Map("PARA" -> Param))
  }

  @Test
  def val_class_params_passed_into_super_constructor(): Unit = {
    checkSymbolClassification("""
      class A(val xxxxxx: String) extends RuntimeException(xxxxxx)
      """, """
      class A(val $TVAL$: String) extends RuntimeException($PARA$)
      """,
      Map("TVAL" -> TemplateVal, "PARA" -> Param))
  }

  @Test
  def class_params_passed_into_super_constructor_as_expression(): Unit = {
    checkSymbolClassification("""
      class A(xxxxxx: String) extends RuntimeException(xxxxxx + "")
      """, """
      class A($PARA$: String) extends RuntimeException($PARA$ + "")
      """,
      Map("PARA" -> Param))
  }

  @Test
  def self_references_with_ascription(): Unit = {
    checkSymbolClassification("""
      class A { self: AnyRef =>
        self
      }""", """
      class A { $TV$: AnyRef =>
        $TV$
      }""",
      Map("TV" -> TemplateVal))
  }

  @Test
  def static_import(): Unit = {
    checkSymbolClassification("""
      import java.io.File.pathSeparator
      object A {
        pathSeparator
      }""", """
      import java.io.File.$   TVAL    $
      object A {
        $   TVAL    $
      }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def created_from_pair(): Unit = {
    checkSymbolClassification("""
      class X {
        val (xxxxxx, yyyyyy) = (1, 2)
      }""", """
      class X {
        val ($TVAL$, $TVAL$) = (1, 2)
      }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  def created_from_case_deconstruction(): Unit = {
    checkSymbolClassification("""
      class X {
        val Some(xxxxxx) = Some(42)
      }""", """
      class X {
        val Some($TVAL$) = Some(42)
      }""",
      Map("TVAL" -> TemplateVal))
  }

  @Test
  @Ignore("does not work until presentation compiler stores more information in the AST (ticket #1001261)")
  def in_package_object(): Unit = {
    checkSymbolClassification("""
      object X {
        val pv = packageObject.packageVal
      }
      package object packageObject {
        val packageVal = 42
      }
      """, """
      object X {
        val pv = $POBJ       $.$TVAL    $
      }
      package object $POBJ       $ {
        val $TVAL    $ = 42
      }
      """,
      Map("TVAL" -> TemplateVal, "POBJ" -> Package))
  }

}
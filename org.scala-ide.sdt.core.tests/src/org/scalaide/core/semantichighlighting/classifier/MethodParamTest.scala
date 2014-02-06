package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit._

class MethodParamTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_parameter() {
    checkSymbolClassification("""
      object A {
        {
           def method(ppppppp: Int) = ppppppp
        }
      }""", """
      object A {
        {
           def method($PARAM$: Int) = $PARAM$
        }
      }""",
      Map("PARAM" -> Param))
  }

  @Test
  def function_param() {
    checkSymbolClassification("""
      object A {
        {
           List(3) map { ppppppp => ppppppp * 2 }
        }
      }""", """
      object A {
        {
           List(3) map { $PARAM$ => $PARAM$ * 2 }
        }
      }""",
      Map("PARAM" -> Param))
  }

  @Test
  def named_arguments() {
    checkSymbolClassification("""
      object A {
           def foo(ppppppp: String) = 42

        {
           foo(ppppppp = "wibble")
        }
      }""", """
      object A {
           def foo($PARAM$: String) = 42

        {
           foo($PARAM$ = "wibble")
        }
      }""",
      Map("PARAM" -> Param))
  }

  @Test
  def annotation_arguments() {
    checkSymbolClassification("""
      @SuppressWarnings(value = Array("all"))
      class A
      """, """
      @SuppressWarnings($PRM$ = Array("all"))
      class A
      """,
      Map("PRM" -> Param))
  }

  @Test
  def case_constructor_arguments() {
    checkSymbolClassification("""
      case class Bob(param: Int) {
        Bob(param = 42)
      }""", """
      case class Bob(param: Int) {
        Bob($PRM$ = 42)
      }""",
      Map("PRM" -> Param))
  }
}
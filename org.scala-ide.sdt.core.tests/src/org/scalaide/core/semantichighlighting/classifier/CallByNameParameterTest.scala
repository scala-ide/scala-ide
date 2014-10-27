package org.scalaide.core.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import org.junit.Test

class CallByNameParameterTest extends AbstractSymbolClassifierTest {
  @Test
  def simple_method() {
    checkSymbolClassification("""
      object O {
        def test(cbnp0: => Int) = cbnp0
      }
      """, """
      object O {
        def test($  P$: => Int) = $BNP$
      }
      """,
      Map("BNP" -> CallByNameParameter, "P" -> Param))
  }

  @Test
  def with_multiple_args() {
    checkSymbolClassification("""
      object O {
        def test(cbnp1: => Int, x: String, cbnp3: => Long): Long = {
          val hx = x.hashCode
          val h3 = cbnp3.hashCode
          cbnp1 + hx + h3
        }
      }
      """, """
      object O {
        def test($  P$: => Int, x: String, $  P$: => Long): Long = {
          val hx = x.hashCode
          val h3 = $BNP$.hashCode
          $BNP$ + hx + h3
        }
      }
      """,
      Map("BNP" -> CallByNameParameter, "P" -> Param))
  }

  @Test
  def with_ctor() {
    checkSymbolClassification("""
      class Clazz(s1: String, cbnp1: => Int, s2: String, cbnp2: => Int) {
        lazy val p1 = cbnp1
        lazy val p2 = cbnp2
      }
      """, """
      class Clazz(s1: String, $  P$: => Int, s2: String, $  P$: => Int) {
        lazy val p1 = $BNP$
        lazy val p2 = $BNP$
      }
      """, Map("BNP" -> CallByNameParameter, "P" -> Param))
  }

  @Test
  def with_for_comprehension() {
    checkSymbolClassification("""
      object O {
        def foo(cbnp0: => Int) = {
          val numbers = for (cbnp0 <- 1 to 3) yield i
          numbers.sum + cbnp0
        }
      }
      """, """
      object O {
        def foo($P  $: => Int) = {
          val numbers = for ($LV $ <- 1 to 3) yield i
          numbers.sum + $BNP$
        }
      }
      """, Map("BNP" -> CallByNameParameter, "LV" -> LocalVal, "P" -> Param))
  }
}
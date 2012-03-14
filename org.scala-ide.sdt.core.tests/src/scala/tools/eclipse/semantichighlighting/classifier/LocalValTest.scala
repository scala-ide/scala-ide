package scala.tools.eclipse.semantichighlighting.classifier

import SymbolTypes._
import org.junit._

class LocalValTest extends AbstractSymbolClassifierTest {

  @Test
  def basic_decl() {
    checkSymbolClassification("""
      object A {
        {
           val xxxxxx = 100
        }
      }""", """
      object A {
        {
           val $LVAL$ = 100
        }
      }""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def basic_decl_and_ref() {
    checkSymbolClassification("""
      object A {
        {
           val xxxxxx = 100
           xxxxxx * xxxxxx
        }
      }""", """
      object A {
        {
           val $LVAL$ = 100
           $LVAL$ * $LVAL$
        }
      }""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def symbol_decl() {
    checkSymbolClassification("""
      object A {
        {
           val :::::: = 100
        }
      }""", """
      object A {
        {
           val $LVAL$ = 100
        }
      }""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def decl_and_ref_of_backticked_identifiers() {
    checkSymbolClassification("""
      object A {
        {
           val `xxxx` = 100
           `xxxx` * `xxxx`
        }
      }""", """
      object A {
        {
           val $LVAL$ = 100
           $LVAL$ * $LVAL$
        }
      }""",
      Map("LVAL" -> LocalVal))
  }


  @Test
  def pair_pattern() {
    checkSymbolClassification("""
      object A {
        {
           val (xxxxx, yyyyy) = (1, 2)
           xxxxx * yyyyy
        }
      }""", """
      object A {
        {
           val ($LV1$, $LV2$) = (1, 2)
           $LV2$ * $LV2$
        }
      }""",
      Map("LV1" -> LocalVal, "LV2" -> LocalVal))
  }

  @Test
  def at_binding() {
    checkSymbolClassification("""
      object A {
        {
           val xxxxxx @ List(_) = Nil
        }
      }""", """
      object A {
        {
           val $LVAL$ @ List(_) = Nil
        }
      }""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def type_ascription_bug() {
    checkSymbolClassification("""
      object A {
        {
           val List(xxxxxx: Int) = List(42)
        }
      }""", """
      object A {
        {
           val List($LVAL$: Int) = List(42)
        }
      }""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def for_generators {
    checkSymbolClassification("""
      object A {
        for (nnnnnn <- 1 to 100) println()
      }
""", """
      object A {
        for ($LVAL$ <- 1 to 100) println()
      }
""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def paren_for_generators {
    checkSymbolClassification("""
      object A {
        for ((nnnnnn) <- 1 to 100) println()
      }
""", """
      object A {
        for (($LVAL$) <- 1 to 100) println()
      }
""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def ascripted_for_generators {
    checkSymbolClassification("""
      object A {
        for (nnnnnn: Int <- 1 to 100) println()
      }
""", """
      object A {
        for ($LVAL$: Int <- 1 to 100) println()
      }
""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def for_comprehension_cases {
    checkSymbolClassification("""
      object A {
        for (xxxxxx <- "fo") println(xxxxxx)
        for (xxxxxx <- 1 to 100; yyyyyy = 1 to xxxxxx) ()
      }
""", """
      object A {
        for ($LVAL$ <- "fo") println($LVAL$)
        for ($LVAL$ <- 1 to 100; $LVAL$ = 1 to $LVAL$) ()
      }
""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  def more_for_comprehension_cases {
    checkSymbolClassification("""
    object A {
      {
        for (`aaaaaa` <- "fo") ()
        for (BBBBBB <- "fo") ()
        val CCCCCC = 'x'
        for (Some(CCCCCC) <- List(Some('x'), Some('y'))) ()
      }
    }
""", """
    object A {
      {
        for ($ LVAL $ <- "fo") ()
        for ($LVAL$ <- "fo") ()
        val $LVAL$ = 'x'
        for (Some($LVAL$) <- List(Some('x'), Some('y'))) ()
      }
    }
""",
      Map("LVAL" -> LocalVal))
  }

  @Test
  @Ignore
  def tricky_for_comprehension_classification {
    checkSymbolClassification("""
      object A {
        for ((CCCCCC) <- "fo") ()
      }
""", """
      object A {
        for (($LVAL$) <- "fo") ()
      }
""",
      Map("LVAL" -> LocalVal))
  }

}
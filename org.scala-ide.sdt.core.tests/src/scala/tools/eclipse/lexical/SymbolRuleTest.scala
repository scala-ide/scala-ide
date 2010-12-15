package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.eclipse.jface.text.rules._

class SymbolRuleTest extends RuleTest {

  import TestLanguage._

  @Test
  def positiveCases() { // format: OFF
    "'Symbol"     ==> "'Symbol"
    "'//"         ==> "'//"
    "'/*"         ==> "'/*"
    "'//**/"      ==> "'/"
    "'///"        ==> "'/"
    "'X"          ==> "'X"
    "'Sym+y"      ==> "'Sym"
    "'Sym_"       ==> "'Sym_"
    "'::"         ==> "'::"
    "'_"          ==> "'_"
    "'$"          ==> "'$"
    "'Sym_bar_::" ==> "'Sym_bar_::"
    "'c,J(\"\"\"" ==> "'c"
  } // format: ON

  @Test
  def negativeCases() {
    doesNotRecognise("'c'")
    doesNotRecognise("'")
    doesNotRecognise("' ")
    doesNotRecognise("'\"")
    doesNotRecognise("''")
    doesNotRecognise("Foo")
  }

  def rule(token: IToken) = new SymbolRule(token)

}


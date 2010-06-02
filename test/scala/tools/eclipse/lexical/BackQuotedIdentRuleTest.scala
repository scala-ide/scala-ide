package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.eclipse.jface.text.rules._

class BackQuotedIdentRuleTest extends RuleTest {

  import TestLanguage._

  @Test
  def positiveCases() { // format: OFF
    "`val`"       ==> "`val`"
    "`val` = 0"   ==> "`val`"
    "`val"        ==> "`val"
    "`"           ==> "`"
    "```"         ==> "``"
    "`val\r\nfoo" ==> "`val\r\n"
    "`val\nfoo"   ==> "`val\n"
  } // format: ON

  @Test
  def negativeCases() {
    doesNotRecognise("")
    doesNotRecognise("'")
    doesNotRecognise("x")
  }

  def rule(token: IToken) = new BackQuotedIdentRule(token)

}
 

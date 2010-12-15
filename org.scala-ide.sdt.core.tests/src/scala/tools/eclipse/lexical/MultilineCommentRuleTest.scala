package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.runner.RunWith
import org.junit.{ Test, Before }
import org.eclipse.jface.text.rules._

class MultilineCommentRuleTest extends RuleTest {

  import TestLanguage._

  @Test
  def positiveCases() { // format: OFF
    "/* foo */"                ==> "/* foo */"
    "/* foo */42"              ==> "/* foo */"
    "/* foo */*/"              ==> "/* foo */"
    "/* /* bar */ */ X"        ==> "/* /* bar */ */"
    "/*/* bar *//* baz */*/ X" ==> "/*/* bar *//* baz */*/"
    "/* * / * / */ X"          ==> "/* * / * / */"
    "/*\n*/"                   ==> "/*\n*/"
    "/*/"                      ==> "/*/"
    "/**/"                     ==> "/**/"
    "/* unterminated..."       ==> "/* unterminated..."
  } // format: ON

  @Test
  def negativeCases() {
    doesNotRecognise("")
    doesNotRecognise("/")
    doesNotRecognise("*/")
  }

  @Test
  def scalaDocCases() {
    scalaDoc = true

    "/** foo*/42" ==> "/** foo*/"
    "/***/42" ==> "/***/"

    doesNotRecognise("/* foo */")
    doesNotRecognise("/**/")
  }

  @Before
  def setup { scalaDoc = false }

  private var scalaDoc: Boolean = _

  def rule(token: IToken) = new MultilineCommentRule(token, scalaDoc)

}


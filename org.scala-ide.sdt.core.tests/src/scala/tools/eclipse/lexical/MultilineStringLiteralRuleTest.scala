package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.{ Test, Before }
import org.eclipse.jface.text.rules._

class MultilineStringLiteralRuleTest extends RuleTest {

  import TestLanguage._
  @Test
  def positiveCases() { // format: OFF
    <t>"""wibble"""</t>                ==> <t>"""wibble"""</t>;
    <t>"""wibble""" + x</t>            ==> <t>"""wibble"""</t>;
    <t>"""wibble{nl}"""</t>            ==> <t>"""wibble{nl}"""</t>;
    <t>""""""+1</t>                    ==> <t>""""""</t>;
    <t>"""""""+1</t>                   ==> <t>"""""""</t>;
    <t>""""""""+1</t>                  ==> <t>""""""""</t>;
    <t>"""""Foo"""""+1</t>             ==> <t>"""""Foo"""""</t>;
    <t>"""Foo\"""+1</t>                ==> <t>"""Foo\"""</t>; // See #3008
    <t>"""wibble</t>                   ==> <t>"""wibble</t>;
    <t>"""</t>                         ==> <t>"""</t>;
  } // format: ON

  @Test
  def negativeCases() {
    doesNotRecognise(<t>""</t>)
    doesNotRecognise(<t>"" ""</t>)
  }

  def rule(token: IToken) = new MultilineStringLiteralRule(token)

}


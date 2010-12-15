package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.internal.runners.JUnit4ClassRunner
import org.junit.runner.RunWith
import org.junit.Test
import org.eclipse.jface.text.rules._
import scala.xml.Elem

trait RuleTest {

  protected def doesNotRecognise(s: String) = assertEquals(None, runRule(s))
  protected def doesNotRecognise(elem: Elem) = assertEquals(None, runRule(elem.text))

  private def runRule(s: String): Option[String] = {
    val scanner = new StringCharacterScanner(s)
    val dummyToken = new Token(None)
    val token = rule(dummyToken).evaluate(scanner)
    if (token.isUndefined) None else Some(scanner.consumed)
  }

  def rule(token: IToken): IPredicateRule

  val nl = "\n"
  
  object TestLanguage {
    implicit def string2Wrapper(s: String): Wrapper = new Wrapper(s)
    implicit def elem2Wrapper(elem: Elem): Wrapper = new Wrapper(elem.text)

    class Wrapper(s: String) {
      def ==>(expected: String) { assertEquals(Some(expected), runRule(s)) }
      def ==>(expected: Elem) { this ==> expected.text }
    }
  }

}


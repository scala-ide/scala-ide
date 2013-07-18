package scala.tools.eclipse.lexical

import scala.tools.eclipse.properties.syntaxcolouring._
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scala.tools.eclipse.testsetup.SDTTestUtils

import org.junit.{ ComparisonFailure, Ignore, Test }

import scalariform.ScalaVersions

class ScalaCodeScannerTest {

  /**
   * Tokenizes the complete content as a Scala source code snippet.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is the `ScalaSyntaxClass` specifying
   * the the type of the token. The second element is the offset of the token
   * and the last element is its length.
   */
  def tokenize(str: String): Seq[(ScalaSyntaxClass, Int, Int)] =
    tokenize(str, 0, str.length())

  /**
   * Tokenizes the complete content as a Scala source code snippet. It is
   * possible to tokenize only a specific range, specified by its offset and
   * its length.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is the `ScalaSyntaxClass` specifying
   * the the type of the token. The second element is the offset of the token
   * and the last element is its length.
   */
  def tokenize(str: String, offset: Int, length: Int): Seq[(ScalaSyntaxClass, Int, Int)] = {
    val scanner = new ScalaCodeTokenizer {
      val scalaVersion = ScalaVersions.DEFAULT
    }

    val document = new MockDocument(str)
    val token = scanner.tokenize(document.get(offset, length), offset) map {
      case scanner.Range(start, length, syntaxClass) =>
        (syntaxClass, start, length)
    }
    token.toList
  }

  class Assert_===(actual: Seq[(ScalaSyntaxClass, Int, Int)]) {
    def ===(expected: Seq[(ScalaSyntaxClass, Int, Int)]) {
      val a = actual map { case (syntaxClass, start, length) => (syntaxClass.baseName, start, length) }
      val e = expected map { case (syntaxClass, start, length) => (syntaxClass.baseName, start, length) }
      if (a != e)
        throw new ComparisonFailure("", e.toString, a.toString)
    }
  }
  implicit def Assert_===(actual: Seq[(ScalaSyntaxClass, Int, Int)]): Assert_=== = new Assert_===(actual)

  @Test
  def brackets() {
    "(){}[]" map (x => tokenize(x.toString)) foreach {
      _ === Seq((BRACKET, 0, 1))
    }
  }

  @Test
  def keywords() {
    SDTTestUtils.testWithCompiler("lexical") { compiler =>
      def isAlpha(s: String) = s(0) >= 'a' && s(0) <= 'z'
      val keywords = compiler.nme.keywords map (_.toString) filter isAlpha
      /*
       * Discard some keywords:
       * - macro - not a keyword in 2.9, in 2.10 only in special places
       * - return - needs special handling, treated in its own test case
       * - then - not yet a final keyword in 2.9 or 2.10
       */
      val testableKeywords = keywords filterNot Set("macro", "return", "then")

      testableKeywords map (x => tokenize(x) -> x) foreach {
        case (token, x) => token === Seq((KEYWORD, 0, x.length()))
      }
    }
  }

  @Test
  def return_keyword() {
    tokenize("return") === Seq((RETURN, 0, 6))
  }

  @Test
  def single_macro_keyword_in_source_should_not_produce_exception() {
    tokenize("macro") === Seq((DEFAULT, 0, 5))
  }

  @Test
  def operator_keywords() {
    val xs = "_ : = => <- <: <% >: ⇒ ← . , ; # @"
    xs split " " map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((OPERATOR, 0, x.length()))
    }
  }

  @Test
  def ascii_operators() {
    val os = """!%&*+-<=>?\^|~/""" map (_.toString)
    os map tokenize foreach {
      _ === Seq((OPERATOR, 0, 1))
    }
  }

  @Test
  def number_literals() {
    val xs = "1 1.0 1E1 1E-1 1e1 1D 1d 1F 1f 1L 1l 0x1 0X1 01" split " "
    xs map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((NUMBER_LITERAL, 0, x.length()))
    }
  }

  @Test
  def symbol_literal() {
    tokenize("'symbol") === Seq((SYMBOL, 0, 7))
  }

  @Test
  def requires_is_no_keyword() {
    tokenize("requires") === Seq((DEFAULT, 0, 8))
  }

}
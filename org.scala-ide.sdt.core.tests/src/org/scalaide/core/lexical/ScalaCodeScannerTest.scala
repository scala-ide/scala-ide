package org.scalaide.core
package lexical

import org.scalaide.ui.syntax._
import org.scalaide.ui.syntax.ScalaSyntaxClasses._
import testsetup.SDTTestUtils
import org.junit.ComparisonFailure
import org.junit.Ignore
import org.junit.Test
import scalariform.ScalaVersions
import org.scalaide.core.internal.lexical.ScalaCodeTokenizerScalariformBased
import org.scalaide.core.lexical.ScalaCodeTokenizer.Token

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
    val scanner = new ScalaCodeTokenizerScalariformBased(ScalaVersions.DEFAULT)

    val token = scanner.tokenize(str.slice(offset, length), offset) map {
      case Token(start, length, syntaxClass) =>
        (syntaxClass, start, length)
    }
    token.toList
  }

  implicit class Assert_===(actual: Seq[(ScalaSyntaxClass, Int, Int)]) {
    def ===(expected: Seq[(ScalaSyntaxClass, Int, Int)]): Unit = {
      val a = actual map { case (syntaxClass, start, length) => (syntaxClass.baseName, start, length) }
      val e = expected map { case (syntaxClass, start, length) => (syntaxClass.baseName, start, length) }
      if (a != e)
        throw new ComparisonFailure("", e.toString, a.toString)
    }
  }

  @Test
  def brackets(): Unit = {
    "(){}[]" map (x => tokenize(x.toString)) foreach {
      _ === Seq((BRACKET, 0, 1))
    }
  }

  @Test
  def keywords(): Unit = {
    SDTTestUtils.testWithCompiler("lexical") { compiler =>
      def isAlpha(s: String) = s(0) >= 'a' && s(0) <= 'z'
      val keywords = compiler.nme.keywords map (_.toString) filter isAlpha
      /*
       * Discard some keywords:
       * - macro - only a keyword in special places
       * - return - needs special handling, treated in its own test case
       * - then - not yet a final keyword in 2.10
       */
      val testableKeywords = keywords filterNot Set("macro", "return", "then")

      testableKeywords map (x => tokenize(x) -> x) foreach {
        case (token, x) => token === Seq((KEYWORD, 0, x.length()))
      }
    }
  }

  @Test
  def correct_offset(): Unit = {
    val str = "return"
    val offset = 3 // arbitrary non-zero number

    val scanner = new ScalaCodeTokenizerScalariformBased(ScalaVersions.DEFAULT)
    val token = scanner.tokenize(str, offset) map {
      case Token(start, length, syntaxClass) =>
        (syntaxClass, start, length)
    }

    token.toList == Seq((RETURN, 3, 6))
  }

  @Test
  def return_keyword(): Unit = {
    tokenize("return") === Seq((RETURN, 0, 6))
  }

  @Test
  def single_macro_keyword_in_source_should_not_produce_exception(): Unit = {
    tokenize("macro") === Seq((DEFAULT, 0, 5))
  }

  @Test
  def operator_keywords(): Unit = {
    val xs = "_ : = => <- <: <% >: ⇒ ← . , ; # @"
    xs split " " map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((OPERATOR, 0, x.length()))
    }
  }

  @Test
  def ascii_operators(): Unit = {
    val os = """!%&*+-<=>?\^|~/""" map (_.toString)
    os map tokenize foreach {
      _ === Seq((OPERATOR, 0, 1))
    }
  }

  @Test
  def number_literals(): Unit = {
    val xs = "1 1.0 1E1 1E-1 1e1 1D 1d 1F 1f 1L 1l 0x1 0X1 01" split " "
    xs map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((NUMBER_LITERAL, 0, x.length()))
    }
  }

  @Test
  def symbol_literal(): Unit = {
    tokenize("'symbol") === Seq((SYMBOL, 0, 7))
  }

  @Test
  def requires_is_no_keyword(): Unit = {
    tokenize("requires") === Seq((DEFAULT, 0, 8))
  }

}

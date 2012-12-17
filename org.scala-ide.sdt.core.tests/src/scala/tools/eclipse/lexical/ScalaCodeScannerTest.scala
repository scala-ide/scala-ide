package scala.tools.eclipse.lexical

import scala.tools.eclipse.properties.syntaxcolouring._
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._

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
    val token = scanner.tokenize(document, offset, length) map {
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
    val xs = Seq("abstract", "case", "catch", "class", "def", "do", "else",
      "extends", "false", "final", "finally", "for", "forSome", "if",
      "implicit", "import", "lazy", "match", "new", "null", "object",
      "override", "package", "private", "protected", "sealed", "super",
      "this", "throw", "trait", "try", "true", "type", "val", "var", "while",
      "with", "yield")
    xs map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((KEYWORD, 0, x.length()))
    }
  }

  @Test
  def return_keyword() {
    tokenize("return") === Seq((RETURN, 0, 6))
  }

  @Test
  def symbol_keywords() {
    val xs = Seq("_", ":", "=", "=>", "<-", "<:", "<%", ">:", "⇒", "←", ".", ",", ";")
    xs map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((OPERATOR, 0, x.length()))
    }
  }

  @Ignore("the current behavior of the tokenizing logic is not correct")
  @Test
  def at_and_hash_are_symbol_keywords() {
    tokenize("#") === Seq((OPERATOR, 0, 1))
    tokenize("@") === Seq((OPERATOR, 0, 1))
  }

  @Test
  def ascii_operators() {
    val os = """!%&*+-<=>?\^|~""" map (_.toString)
    os map tokenize foreach {
      _ === Seq((OPERATOR, 0, 1))
    }
  }

  @Test
  def number_literals() {
    val xs = "1 1.0 1E1 1E-1 1e1 1D 1d 1F 1f 1L 1l" split " "
    xs map (x => tokenize(x) -> x) foreach {
      case (token, x) => token === Seq((NUMBER_LITERAL, 0, x.length()))
    }
  }

  @Test
  def symbol_literal() {
    tokenize("'symbol") === Seq((SYMBOL, 0, 7))
  }

}
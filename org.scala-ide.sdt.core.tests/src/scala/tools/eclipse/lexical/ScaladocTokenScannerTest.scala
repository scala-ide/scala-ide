package scala.tools.eclipse.lexical

import org.junit.Test

class ScaladocTokenScannerTest {

  var scaladocAtt: String = _
  var annotationAtt: String = _
  var macroAtt: String = _

  /**
   * Tokenizes Scaladoc content. The complete input is handled as Scaladoc
   * content.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a string specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String): Seq[(String, Int, Int)] =
    tokenize(str, 0, str.length())

  /**
   * Tokenizes Scaladoc content that is placed somewhere in a Scala source code
   * snippet, which is passed in `str`. The Scaladoc content can be referenced by
   * passing its offset and its length. The offset must start at the `/` sign
   * of the Scaladoc starter marker. The length includes start and end tags of
   * Scaladoc.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a string specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String, offset: Int, length: Int): Seq[(String, Int, Int)] = {
    val scanner = new ScaladocTokenizer {}

    scaladocAtt = scanner.Scaladoc.toString
    annotationAtt = scanner.Annotation.toString
    macroAtt = scanner.Macro.toString

    val document = new MockDocument(str)
    val token = scanner.tokenize(document, offset, length) map {
      case scanner.StyleRange(start, end, style) =>
        (style.toString, start, end - start)
    }
    token.toList
  }

  class Assert_===[A](actual: A) {
    def ===(expected: A) {
      if (actual != expected)
        throw new AssertionError("""Expected != Actual
          |Expected: %s
          |Actual:   %s""".stripMargin.format(expected, actual))
    }
  }
  implicit def Assert_===[A](actual: A): Assert_===[A] = new Assert_===(actual)

  @Test
  def no_annotation() {
    val res = tokenize("""/***/""")
    res === Seq((scaladocAtt, 0, 5))
  }

  @Test
  def single_annotation() {
    val res = tokenize("""/**@param world desc*/""")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 6), (scaladocAtt, 9, 13))
  }

  @Test
  def single_annotation_without_text() {
    val res = tokenize("""/**@param*/""")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 6), (scaladocAtt, 9, 2))
  }

  @Test
  def consecutive_annotations_should_be_handled_as_single_annotation() {
    val res = tokenize("""/**@pa@pa*/""")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 6), (scaladocAtt, 9, 2))
  }

  @Test
  def consecutive_macros_should_be_handled_as_single_macro() {
    val res = tokenize("""/**$pa$pa*/""")
    res === Seq((scaladocAtt, 0, 3), (macroAtt, 3, 6), (scaladocAtt, 9, 2))
  }

  @Test
  def identifier_as_name_in_annotation() {
    val res = tokenize("""/**@azAZ09_*/""")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 8), (scaladocAtt, 11, 2))
  }

  @Test
  def identifier_as_name_in_macro() {
    val res = tokenize("""/**$azAZ09_*/""")
    res === Seq((scaladocAtt, 0, 3), (macroAtt, 3, 8), (scaladocAtt, 11, 2))
  }

  @Test
  def multiple_annotation() {
    val res = tokenize("""/**@pa abc @param @param */""")
    res === Seq(
      (scaladocAtt, 0, 3), (annotationAtt, 3, 3), (scaladocAtt, 6, 5),
      (annotationAtt, 11, 6), (scaladocAtt, 17, 1), (annotationAtt, 18, 6),
      (scaladocAtt, 24, 3))
  }

  @Test
  def multiple_macros() {
    val res = tokenize("""/**$def $def$def*/""")
    res === Seq(
      (scaladocAtt, 0, 3), (macroAtt, 3, 4), (scaladocAtt, 7, 1),
      (macroAtt, 8, 8), (scaladocAtt, 16, 2))
  }

  @Test
  def consecutive_annotations_or_macros_should_be_highlighted() {
    val res = tokenize("""/**@param$def@param*/""")
    res === Seq(
      (scaladocAtt, 0, 3), (annotationAtt, 3, 6), (macroAtt, 9, 4),
      (annotationAtt, 13, 6), (scaladocAtt, 19, 2))
  }

  @Test
  def single_line_in_scaladoc_should_not_produce_an_out_of_bound_error() {
    val res = tokenize(" * @param")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 6))
  }

  @Test
  def no_highlighting_of_a_single_start_symbol() {
    val res = tokenize("a @ b $")
    res === Seq((scaladocAtt, 0, 7))
  }

  @Test
  def start_symbol_between_identifiers_should_handled_as_scaladoc() {
    val res = tokenize("a@b a$b")
    res === Seq((scaladocAtt, 0, 7))
  }

  @Test
  def no_highlighting_of_start_symbol_before_another_start_symbol() {
    val res = tokenize("@@ab $$ab")
    res === Seq(
      (scaladocAtt, 0, 1), (annotationAtt, 1, 3), (scaladocAtt, 4, 2),
      (macroAtt, 6, 3))
  }

  @Test
  def no_highlighting_of_start_symbol_after_non_scaladoc() {
    val res = tokenize("@ab@ $ab$")
    res === Seq(
      (annotationAtt, 0, 3), (scaladocAtt, 3, 2), (macroAtt, 5, 3),
      (scaladocAtt, 8, 1))
  }

  @Test
  def part_of_source_code_snippet() {
    val res = tokenize("val i = 0; /**@param $def*/ def meth = 0", 11, 16)
    res === Seq(
      (scaladocAtt, 11, 3), (annotationAtt, 14, 6), (scaladocAtt, 20, 1),
      (macroAtt, 21, 4), (scaladocAtt, 25, 2))
  }

}
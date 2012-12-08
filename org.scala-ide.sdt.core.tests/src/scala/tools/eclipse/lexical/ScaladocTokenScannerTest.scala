package scala.tools.eclipse.lexical

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass

import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.TextAttribute
import org.eclipse.jface.text.rules.Token
import org.junit.Test
import org.mockito.Mockito._

class ScaladocTokenScannerTest {

  var scaladocAtt: TextAttribute = _
  var annotationAtt: TextAttribute = _
  var macroAtt: TextAttribute = _

  /**
   * Tokenizes Scaladoc content. The complete input is handled as Scaladoc
   * content.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a `TextAttribute` specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String): Seq[(TextAttribute, Int, Int)] =
    tokenize(str, 0, str.length())

  /**
   * Tokenizes Scaladoc content that is placed somewhere in a Scala source code
   * snippet, which is passed in `str`. The Scaladoc content can be referenced by
   * passing its offset and its length. The offset must start at the `/` sign
   * of the Scaladoc starter marker. The length includes start and end tags of
   * Scaladoc.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a `TextAttribute` specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String, offset: Int, length: Int): Seq[(TextAttribute, Int, Int)] = {
    val scalaPreferenceStore = mock(classOf[IPreferenceStore])
    val colorManager = mock(classOf[IColorManager])

    val scaladocClass = mock(classOf[ScalaSyntaxClass])
    scaladocAtt = mock(classOf[TextAttribute])
    when(scaladocClass.getTextAttribute(scalaPreferenceStore)).thenReturn(scaladocAtt)
    when(scaladocAtt.toString()).thenReturn("scaladocTextAttribute")

    val annotationClass = mock(classOf[ScalaSyntaxClass])
    annotationAtt = mock(classOf[TextAttribute])
    when(annotationClass.getTextAttribute(scalaPreferenceStore)).thenReturn(annotationAtt)
    when(annotationAtt.toString()).thenReturn("annotationTextAttribute")

    val macroClass = mock(classOf[ScalaSyntaxClass])
    macroAtt = mock(classOf[TextAttribute])
    when(macroClass.getTextAttribute(scalaPreferenceStore)).thenReturn(macroAtt)
    when(macroAtt.toString()).thenReturn("macroTextAttribute")

    val scanner = new ScaladocTokenScanner(
      scaladocClass, annotationClass, macroClass,
      colorManager, scalaPreferenceStore)

    val document = new MockDocument(str)
    scanner.setRange(document, offset, length)

    val iter = Iterator continually { (scanner.nextToken(), scanner.getTokenOffset(), scanner.getTokenLength()) }
    val data = iter takeWhile { _._1 != Token.EOF } map {
      case (token, offset, length) => (token.getData().asInstanceOf[TextAttribute], offset, length)
    }

    data.toList
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
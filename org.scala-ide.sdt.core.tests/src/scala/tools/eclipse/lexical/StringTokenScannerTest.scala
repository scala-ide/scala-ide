package scala.tools.eclipse.lexical

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass

import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.TextAttribute
import org.eclipse.jface.text.rules.Token
import org.junit.Test
import org.mockito.Mockito._

class StringTokenScannerTest {

  var escapeAtt: TextAttribute = _
  var stringAtt: TextAttribute = _

  /**
   * Tokenizes a string literal and only a string literal. It is not allowed to
   * pass anything else to `str`.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a `TextAttribute` specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String): Seq[(TextAttribute, Int, Int)] =
    tokenize(str, 0, str.length())

  /**
   * Tokenizes a string literal that is placed somewhere in a Scala source code
   * snippet, which is passed in `str`. The string literal can be referenced by
   * passing its offset and its length. The offset must start at the `"` sign
   * and not at the first sign of the string. The length includes the `"` signs.
   *
   * There is a sequence returned containing tuples where each tuple value
   * represents a token. The first element is a `TextAttribute` specifying the
   * content of the token. The second element is the offset of the token and the
   * last element is its length.
   */
  def tokenize(str: String, offset: Int, length: Int): Seq[(TextAttribute, Int, Int)] = {
    val scalaPreferenceStore = mock(classOf[IPreferenceStore])
    val colorManager = mock(classOf[IColorManager])

    val escapeClass = mock(classOf[ScalaSyntaxClass])
    escapeAtt = mock(classOf[TextAttribute])
    when(escapeClass.getTextAttribute(scalaPreferenceStore)).thenReturn(escapeAtt)
    when(escapeAtt.toString()).thenReturn("escapeSequenceTextAttribute")

    val stringClass = mock(classOf[ScalaSyntaxClass])
    stringAtt = mock(classOf[TextAttribute])
    when(stringClass.getTextAttribute(scalaPreferenceStore)).thenReturn(stringAtt)
    when(stringAtt.toString()).thenReturn("stringTextAttribute")

    val scanner = new StringTokenScanner(
      escapeClass, stringClass,
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
  def escape_sequence_at_begin_of_string() {
    val res = tokenize(""""\nhello"""")
    res === Seq((stringAtt, 0, 1), (escapeAtt, 1, 2), (stringAtt, 3, 6))
  }

  @Test
  def escape_sequence_at_end_of_string() {
    val res = tokenize(""""hello\n"""")
    res === Seq((stringAtt, 0, 6), (escapeAtt, 6, 2), (stringAtt, 8, 1))
  }

  @Test
  def escape_sequence_in_middle_of_string() {
    val res = tokenize(""""hel\nlo"""")
    res === Seq((stringAtt, 0, 4), (escapeAtt, 4, 2), (stringAtt, 6, 3))
  }

  @Test
  def all_possible_escape_sequences() {
    val res = tokenize(""""\b\t\n\f\r\"\'\\"""")
    res === Seq((stringAtt, 0, 1), (escapeAtt, 1, 16), (stringAtt, 17, 1))
  }

  @Test
  def invalid_escape_sequences() {
    val res = tokenize(""""\m"""")
    res === Seq((stringAtt, 0, 4))
  }

  @Test
  def no_escape_sequence() {
    val res = tokenize(""""hello"""")
    res === Seq((stringAtt, 0, 7))
  }

  @Test
  def double_backslash() {
    val res = tokenize(""""h\\el\\lo"""")
    res === Seq(
      (stringAtt, 0, 2), (escapeAtt, 2, 2), (stringAtt, 4, 2),
      (escapeAtt, 6, 2), (stringAtt, 8, 3))
  }

  @Test
  def consecutive_escape_sequences() {
    val res = tokenize(""""hel\n\n\nlo"""")
    res === Seq((stringAtt, 0, 4), (escapeAtt, 4, 6), (stringAtt, 10, 3))
  }

  @Test
  def multiple_escape_sequences() {
    val res = tokenize(""""hel\n\nl\no"""")
    res === Seq(
      (stringAtt, 0, 4), (escapeAtt, 4, 4), (stringAtt, 8, 1),
      (escapeAtt, 9, 2), (stringAtt, 11, 2))
  }

  @Test
  def string_is_part_of_source_code_snippet() {
    val res = tokenize("""object X { val str = "hel\nlo" }""", 21, 9)
    res === Seq((stringAtt, 21, 4), (escapeAtt, 25, 2), (stringAtt, 27, 3))
  }

  @Test
  def single_unicode_sign() {
    val res = tokenize("\"he\\u006Clo\"")
    res === Seq((stringAtt, 0, 3), (escapeAtt, 3, 6), (stringAtt, 9, 3))
  }

  @Test
  def multiple_unicode_signs() {
    val res = tokenize("\"he\\u006C\\u006Co\"")
    res === Seq((stringAtt, 0, 3), (escapeAtt, 3, 12), (stringAtt, 15, 2))
  }

  @Test
  def invalid_unicode_signs() {
    val res = tokenize("\"\\u0\\u00\\u006\\u\"")
    res === Seq((stringAtt, 0, 16))
  }

  @Test
  def single_octal_sign() {
    val res = tokenize(""""\123"""")
    res === Seq((stringAtt, 0, 1), (escapeAtt, 1, 4), (stringAtt, 5, 1))
  }

  @Test
  def multiple_octal_signs() {
    val res = tokenize(""""\0\12\377\38\400"""")
    res === Seq(
      (stringAtt, 0, 1), (escapeAtt, 1, 11), (stringAtt, 12, 1),
      (escapeAtt, 13, 3), (stringAtt, 16, 2))
  }

  @Test
  def invalid_octal_signs() {
    val res = tokenize(""""\8\9"""")
    res === Seq((stringAtt, 0, 6))
  }
}
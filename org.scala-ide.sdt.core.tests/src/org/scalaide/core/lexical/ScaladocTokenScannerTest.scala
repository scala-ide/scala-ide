package org.scalaide.core.lexical

import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.TextAttribute
import org.eclipse.jface.text.rules.Token
import org.junit.ComparisonFailure
import org.junit.Test
import org.mockito.Mockito._
import org.scalaide.core.internal.lexical.ScaladocTokenScanner
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner

class ScaladocTokenScannerTest {

  var scaladocAtt: String = _
  var annotationAtt: String = _
  var macroAtt: String = _
  var taskTagAtt: String = _

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
    val scaladocClass = mock(classOf[ScalaSyntaxClass])
    val annotationClass = mock(classOf[ScalaSyntaxClass])
    val macroClass = mock(classOf[ScalaSyntaxClass])
    val taskTagClass = mock(classOf[ScalaSyntaxClass])

    val prefStore = mock(classOf[IPreferenceStore])

    val sampleTaskTags = "XXX,TODO,@todo,$todo,!todo"
    when(prefStore.getString(JavaCore.COMPILER_TASK_TAGS)).thenReturn(sampleTaskTags)

    val scaladocAtt = mock(classOf[TextAttribute])
    val annotationAtt = mock(classOf[TextAttribute])
    val macroAtt = mock(classOf[TextAttribute])
    val taskTagAtt = mock(classOf[TextAttribute])

    when(scaladocAtt.toString()).thenReturn("scaladocAtt")
    when(annotationAtt.toString()).thenReturn("annotationAtt")
    when(macroAtt.toString()).thenReturn("macroAtt")
    when(taskTagAtt.toString()).thenReturn("taskTagAtt")

    this.scaladocAtt = scaladocAtt.toString()
    this.annotationAtt = annotationAtt.toString()
    this.macroAtt = macroAtt.toString()
    this.taskTagAtt = taskTagAtt.toString()

    when(scaladocClass.getTextAttribute(prefStore)).thenReturn(scaladocAtt)
    when(annotationClass.getTextAttribute(prefStore)).thenReturn(annotationAtt)
    when(macroClass.getTextAttribute(prefStore)).thenReturn(macroAtt)
    when(taskTagClass.getTextAttribute(prefStore)).thenReturn(taskTagAtt)

    val scanner = new ScaladocTokenScanner(
        prefStore,
        scaladocClass,
        annotationClass,
        macroClass,
        taskTagClass)

    val doc = {
      val rawInput = str.filterNot(_ == '^')
      val doc = new Document(rawInput)
      val partitioner = new ScalaDocumentPartitioner

      doc.setDocumentPartitioner(partitioner)
      partitioner.connect(doc)
      doc
    }

    scanner.setRange(doc, offset, length)

    val data = Iterator
      .continually((scanner.nextToken(), scanner.getTokenOffset(), scanner.getTokenLength()))
      .takeWhile(_._1 != Token.EOF)
      .map { case (ta, off, len) => (ta.getData().toString(), off, len) }
      .toSeq

    /*
     * The scanner returns a token for each character but we want all consecutive
     * token of the same type grouped as one single token.
     */
    val groupedToken = (Seq(data.head) /: data.tail) {
      case (token, t @ (scc, off, len)) =>
        val (sccBefore, offBefore, lenBefore) = token.last
        if (sccBefore == scc)
          token.init :+ ((scc, offBefore, lenBefore+len))
        else
          token :+ t
    }

    groupedToken
  }

  implicit class Assert_===[A](actual: A) {
    def ===(expected: A) {
      if (actual != expected)
        throw new ComparisonFailure("actual != expected,",
          expected.toString(),
          actual.toString())
    }
  }

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
  def consecutive_annotations_should_not_be_handled_as_single_annotation() {
    val res = tokenize("""/**@pa@pa*/""")
    res === Seq((scaladocAtt, 0, 3), (annotationAtt, 3, 3), (scaladocAtt, 6, 5))
  }

  @Test
  def consecutive_macros_should_not_be_handled_as_single_macro() {
    val res = tokenize("""/**$pa$pa*/""")
    res === Seq((scaladocAtt, 0, 3), (macroAtt, 3, 3), (scaladocAtt, 6, 5))
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
    val res = tokenize("""/**$def $def text $def*/""")
    res === Seq(
      (scaladocAtt, 0, 3), (macroAtt, 3, 4), (scaladocAtt, 7, 1),
      (macroAtt, 8, 4), (scaladocAtt, 12, 6), (macroAtt, 18, 4),
      (scaladocAtt, 22, 2))
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

  @Test
  def single_task_tag() {
    val res = tokenize("/**TODO*/")
    res === Seq((scaladocAtt, 0, 3), (taskTagAtt, 3, 4), (scaladocAtt, 7, 2))
  }

  @Test
  def braces_macro() {
    val res = tokenize("/**${abc}d*/")
    res === Seq((scaladocAtt, 0, 3), (macroAtt, 3, 6), (scaladocAtt, 9, 3))
  }

  @Test
  def task_tag_that_starts_with_a_special_sign() {
    val res = tokenize("/**@todo$todo!todo@param*/")
    res === Seq((scaladocAtt, 0, 3), (taskTagAtt, 3, 15), (scaladocAtt, 18, 8))
  }

}
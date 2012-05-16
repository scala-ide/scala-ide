package scala.tools.eclipse.lexical

import org.junit.Assert._
import org.junit.{ Test, Before }

import org.eclipse.jface.text._

class ScalaDocumentPartitionerTest {

  @Test
  def no_partition_change {
    //       000000000011111111112222222222333333333344444444445
    //       012345678901234567890123456789012345678901234567890 
    check("""/* comment */ "foo" /* comment */""", Replace(start = 5, finish = 7, text = "foo"), expectedNoRegion)
  }

  @Test
  def modify_single_partition {
    //       000000000011111111112222222222333333333344444444445
    //       012345678901234567890123456789012345678901234567890 
    check("""/* comment */ "foo" /* comment */""", Insertion(point = 16, text = "XXX"), expectedNoRegion)
    check("""/* comment */ "foo" /* comment *//* comment */""", Replace(start = 14, finish = 18, text = "/* */"), expectedRegion(14, 5))
  }

  @Test
  def delete_partition_at_start_and_end_of_file {
    //       000000000011111111112222222222333333333344444444445
    //       012345678901234567890123456789012345678901234567890 
    check("""/* comment */ 42""", Deletion(start = 0, finish = 12), expectedRegion(0, 0))
    check("""/* comment */ 42""", Deletion(start = 0, finish = 15), expectedRegion(0, 0))
    check("""/* comment */ 42""", Deletion(start = 13, finish = 15), expectedRegion(13, 0))
  }

  private def expectedRegion(offset: Int, length: Int) = new Region(offset, length)

  private def expectedNoRegion: IRegion = null

  private def check(source: String, replacement: Replacement, expectedRegion: IRegion) {
    val partitioner = new ScalaDocumentPartitioner
    val actualRegion = changedPartitionsRegion(partitioner, source, replacement)
    assertEquals(expectedRegion, actualRegion)
  }

  private def changedPartitionsRegion(partitioner: IDocumentPartitioner with IDocumentPartitionerExtension, source: String, replacement: Replacement): IRegion = {
    implicit val doc = new Document(source)
    partitioner.connect(doc)
    val documentEvent = replacement.docEvent
    doc.replace(documentEvent.getOffset, documentEvent.getLength, documentEvent.getText)
    partitioner.documentChanged2(documentEvent)
  }

  sealed trait Replacement {
    def docEvent(implicit doc: IDocument): DocumentEvent
  }

  case class Replace(start: Int, finish: Int, text: String) extends Replacement {
    def docEvent(implicit doc: IDocument): DocumentEvent = new DocumentEvent(doc, start, finish - start + 1, text)
  }

  case class Deletion(start: Int, finish: Int) extends Replacement {
    def docEvent(implicit doc: IDocument): DocumentEvent = new DocumentEvent(doc, start, finish - start + 1, "")
  }

  case class Insertion(point: Int, text: String) extends Replacement {
    def docEvent(implicit doc: IDocument): DocumentEvent = new DocumentEvent(doc, point, 0, text)
  }

}

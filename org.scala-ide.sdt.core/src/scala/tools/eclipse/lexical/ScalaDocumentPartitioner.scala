package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE
import scala.collection.mutable.ListBuffer

class ScalaDocumentPartitioner extends IDocumentPartitioner {

  private var documentOpt: Option[IDocument] = None

  private var tokensOpt: Option[List[ScalaPartitionRegion]] = None

  def connect(document: IDocument): Unit = {
    this.documentOpt = Some(document)
    this.tokensOpt = Some(ScalaPartitionTokeniser.tokenise(document))
  }

  def disconnect(): Unit = { this.documentOpt = None }

  def documentAboutToBeChanged(event: DocumentEvent) {}

  def documentChanged(event: DocumentEvent): Boolean = {
    this.tokensOpt = Some(ScalaPartitionTokeniser.tokenise(documentOpt.get))
    true
  }

  def getLegalContentTypes(): Array[String] = ScalaDocumentPartitioner.LEGAL_CONTENT_TYPES

  def getContentType(offset: Int): String = getToken(offset) map { _.contentType } getOrElse DEFAULT_CONTENT_TYPE

  def getToken(offset: Int): Option[ScalaPartitionRegion] = tokensOpt.get find { _ containsPosition offset }

  def computePartitioning(offset: Int, length: Int): Array[ITypedRegion] = {
    val regions = new ListBuffer[ITypedRegion]
    var searchingForStart = true
    for (token <- tokensOpt.get)
      if (searchingForStart) {
        if (token containsPosition offset) {
          searchingForStart = false
          regions += token
        }
      } else {
        if (token.start > offset + length - 1)
          return regions.toArray
        else
          regions += token
      }
    regions.toArray
  }

  def getPartition(offset: Int): ITypedRegion = getToken(offset) getOrElse new TypedRegion(offset, 0, "__no_partition_at_all")


}

object ScalaDocumentPartitioner {

  import org.eclipse.jdt.ui.text.IJavaPartitions._
  import scala.tools.eclipse.lexical.ScalaPartitions._

  val LEGAL_CONTENT_TYPES = Array[String](
    DEFAULT_CONTENT_TYPE,
    JAVA_DOC, JAVA_MULTI_LINE_COMMENT, JAVA_SINGLE_LINE_COMMENT, JAVA_STRING, JAVA_CHARACTER,
    SCALA_MULTI_LINE_STRING,
    XML_TAG, XML_CDATA, XML_COMMENT, XML_PI, XML_PCDATA)

  final val EOF = '\u001A'

}


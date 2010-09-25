package scala.tools.eclipse.lexical

import org.eclipse.jface.text._
import org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE
import scala.collection.mutable.ListBuffer

class ScalaDocumentPartitioner extends IDocumentPartitioner with IDocumentPartitionerExtension2 {

  import ScalaDocumentPartitioner._

  private var documentOpt: Option[IDocument] = None

  private var partitionRegionsOpt: Option[List[ScalaPartitionRegion]] = None

  def connect(document: IDocument): Unit = {
    this.documentOpt = Some(document)
    this.partitionRegionsOpt = Some(ScalaPartitionTokeniser.tokenise(document))
  }

  def disconnect() { this.documentOpt = None }

  def documentAboutToBeChanged(event: DocumentEvent) {}

  def documentChanged(event: DocumentEvent): Boolean = {
    this.partitionRegionsOpt = Some(ScalaPartitionTokeniser.tokenise(documentOpt.get))
    true
  }

  def getLegalContentTypes(): Array[String] = LEGAL_CONTENT_TYPES

  def getContentType(offset: Int): String = getToken(offset) map { _.contentType } getOrElse DEFAULT_CONTENT_TYPE

  def getToken(offset: Int): Option[ScalaPartitionRegion] = partitionRegionsOpt.get find { _ containsPosition offset }

  def computePartitioning(offset: Int, length: Int): Array[ITypedRegion] = {
    val regions = new ListBuffer[ITypedRegion]
    var searchingForStart = true
    for (partitionRegion <- partitionRegionsOpt.get)
      if (searchingForStart) {
        if (partitionRegion containsPosition offset) {
          searchingForStart = false
          regions += cropRegion(partitionRegion, offset, length)
        }
      } else {
        if (partitionRegion.start > offset + length - 1)
          return regions.toArray
        else
          regions += cropRegion(partitionRegion, offset, length)
      }
    regions.toArray
  }

  private def cropRegion(region: ScalaPartitionRegion, offset: Int, length: Int): ScalaPartitionRegion = {
    import math.{ max, min }
    val ScalaPartitionRegion(_, start, end) = region
    if (start > offset + length - 1 || end < offset)
      region
    else
      region.copy(start = max(start, offset), end = min(end, offset + length))
  }

  def getPartition(offset: Int): ITypedRegion = getToken(offset) getOrElse new TypedRegion(offset, 0, NO_PARTITION_AT_ALL)

  def getManagingPositionCategories(): Array[String] = null

  def getContentType(offset: Int, preferOpenPartitions: Boolean): String =
    getPartition(offset, preferOpenPartitions).getType

  def getPartition(offset: Int, preferOpenPartitions: Boolean): ITypedRegion = {
    val region = getPartition(offset)
    if (preferOpenPartitions)
      if (region.getOffset == offset && region.getType != IDocument.DEFAULT_CONTENT_TYPE)
        if (offset > 0) {
          val previousRegion = getPartition(offset - 1)
          if (previousRegion.getType == IDocument.DEFAULT_CONTENT_TYPE)
            return previousRegion
        }
    return region
  }

  def computePartitioning(offset: Int, length: Int, includeZeroLengthPartitions: Boolean): Array[ITypedRegion] =
    computePartitioning(offset, length)

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

  val NO_PARTITION_AT_ALL = "__no_partition_at_all"

}


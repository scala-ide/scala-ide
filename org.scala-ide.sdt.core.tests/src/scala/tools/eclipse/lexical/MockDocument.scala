package scala.tools.eclipse.lexical

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.jface.text.IDocumentPartitioningListener
import org.eclipse.jface.text.ITypedRegion
import org.eclipse.jface.text.IPositionUpdater
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.IDocument

class MockDocument(private var s: String) extends IDocument {

  def getChar(offset: Int): Char = s.charAt(offset)

  def getLength(): Int = s.length

  def get(): String = s

  def get(offset: Int, length: Int): String = s.substring(offset, offset+length)

  def set(text: String) { s = text }

  def replace(offset: Int, length: Int, text: String) = set(s.patch(offset, text, length))

  def addDocumentListener(listener: IDocumentListener): Unit = {}

  def removeDocumentListener(listener: IDocumentListener): Unit = {}

  def addPrenotifiedDocumentListener(documentAdapter: IDocumentListener): Unit = {}

  def removePrenotifiedDocumentListener(documentAdapter: IDocumentListener): Unit = {}

  def addPositionCategory(category: String): Unit = {}

  def removePositionCategory(category: String): Unit = {}

  def getPositionCategories() = throw new UnsupportedOperationException

  def containsPositionCategory(category: String): Boolean = { false }

  def addPosition(position: Position): Unit = {}

  def removePosition(position: Position): Unit = {}

  def addPosition(category: String, position: Position): Unit = {}

  def removePosition(category: String, position: Position): Unit = {}

  def getPositions(category: String) = Array()

  def containsPosition(category: String, offset: Int, length: Int): Boolean = { false }

  def computeIndexInCategory(category: String, offset: Int): Int = { 0 }

  def addPositionUpdater(updater: IPositionUpdater): Unit = {}

  def removePositionUpdater(updater: IPositionUpdater): Unit = {}

  def insertPositionUpdater(updater: IPositionUpdater, index: Int): Unit = {}

  def getPositionUpdaters() = throw new UnsupportedOperationException

  def getLegalContentTypes() = throw new UnsupportedOperationException

  def getContentType(offset: Int): String = { null }

  def getPartition(offset: Int): ITypedRegion = { null }

  def computePartitioning(offset: Int, length: Int) = throw new UnsupportedOperationException

  def addDocumentPartitioningListener(listener: IDocumentPartitioningListener): Unit = {}

  def removeDocumentPartitioningListener(listener: IDocumentPartitioningListener): Unit = {}

  def setDocumentPartitioner(partitioner: IDocumentPartitioner): Unit = {}

  def getDocumentPartitioner(): IDocumentPartitioner = { null }

  def getLineLength(line: Int): Int = { 0 }

  def getLineOfOffset(offset: Int): Int = { 0 }

  def getLineOffset(line: Int): Int = { 0 }

  def getLineInformation(line: Int): IRegion = { null }

  def getLineInformationOfOffset(offset: Int): IRegion = { null }

  def getNumberOfLines(): Int = { 0 }

  def getNumberOfLines(offset: Int, length: Int): Int = { 0 }

  def computeNumberOfLines(text: String): Int = { 0 }

  def getLegalLineDelimiters() = Array("\n")

  def getLineDelimiter(line: Int): String = "\n"

  def search(startOffset: Int, findString: String, forwardSearch: Boolean, caseSensitive: Boolean, wholeWord: Boolean): Int = { 0 }

}
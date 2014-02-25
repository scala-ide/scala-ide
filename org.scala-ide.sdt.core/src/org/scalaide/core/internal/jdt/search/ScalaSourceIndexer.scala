package org.scalaide.core.internal.jdt.search

import org.eclipse.jdt.core.search.SearchDocument
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.eclipse.contribution.weaving.jdt.indexerprovider.IIndexerFactory
import org.scalaide.logging.HasLogger

class ScalaSourceIndexerFactory extends IIndexerFactory {
  override def createIndexer(document : SearchDocument) = new ScalaSourceIndexer(document);
}

class ScalaSourceIndexer(document : SearchDocument) extends AbstractIndexer(document) with HasLogger {
  override def indexDocument() {
    logger.info("Indexing document: "+document.getPath)
    ScalaSourceFile.createFromPath(document.getPath).map(_.addToIndexer(this))
  }
}

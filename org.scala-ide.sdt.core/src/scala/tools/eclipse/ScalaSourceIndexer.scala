/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.search.SearchDocument
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.contribution.weaving.jdt.indexerprovider.IIndexerFactory
import scala.tools.eclipse.logging.HasLogger

class ScalaSourceIndexerFactory extends IIndexerFactory {
  override def createIndexer(document : SearchDocument) = new ScalaSourceIndexer(document);
}

class ScalaSourceIndexer(document : SearchDocument) extends AbstractIndexer(document) with HasLogger {
  override def indexDocument() {
    logger.info("Indexing document: "+document.getPath)
    ScalaSourceFile.createFromPath(document.getPath).map(_.addToIndexer(this))
  }
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.internal.logging.Tracer
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.search.SearchDocument
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.contribution.weaving.jdt.indexerprovider.IIndexerFactory

class ScalaSourceIndexerFactory extends IIndexerFactory {
  override def createIndexer(document : SearchDocument) = new ScalaSourceIndexer(document);
}

class ScalaSourceIndexer(document : SearchDocument) extends AbstractIndexer(document) {
  override def indexDocument() {
    Tracer.println("Indexing document: "+ document.getPath)
    try {
      ScalaSourceFile.createFromPath(document.getPath).map(_.addToIndexer(this))
    } catch {
      case exc => {
        //log, ignore and continue
        ScalaPlugin.plugin.logError("failed to index :" + document.getPath, exc)
      }
    }
  }
}

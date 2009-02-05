package scala.tools.eclipse

import org.eclipse.jdt.core.search.SearchDocument
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer

abstract class ScalaSourceIndexer(document : SearchDocument) extends AbstractIndexer(document)

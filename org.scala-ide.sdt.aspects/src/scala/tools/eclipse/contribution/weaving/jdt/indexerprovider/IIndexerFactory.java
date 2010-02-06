/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.indexerprovider;

import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer;

@SuppressWarnings("restriction")
public interface IIndexerFactory {
  public AbstractIndexer createIndexer(SearchDocument document);
}

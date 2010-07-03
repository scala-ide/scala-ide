/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.indexerprovider;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class IndexerProviderRegistry extends AbstractProviderRegistry<IIndexerFactory> {

        public static String INDEXING_PROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.indexerprovider"; //$NON-NLS-1$

	private static final IndexerProviderRegistry INSTANCE = new IndexerProviderRegistry();

	public static IndexerProviderRegistry getInstance() {
		return INSTANCE;
	}

	@Override
	protected String getExtensionPointId() {
	    return INDEXING_PROVIDERS_EXTENSION_POINT;
	}
}

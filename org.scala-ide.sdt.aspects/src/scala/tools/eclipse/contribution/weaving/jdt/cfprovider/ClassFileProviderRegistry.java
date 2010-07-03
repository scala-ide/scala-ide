/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class ClassFileProviderRegistry extends AbstractProviderRegistry<IClassFileProvider> {

	private static final ClassFileProviderRegistry INSTANCE = new ClassFileProviderRegistry();

        public static String CFPROVIDERS_EXTENSION_POINT = "org.scala-ide.sdt.aspects.cfprovider"; //$NON-NLS-1$

	public static ClassFileProviderRegistry getInstance() {
		return INSTANCE;
	}

	@Override
	protected String getExtensionPointId() {
	    return CFPROVIDERS_EXTENSION_POINT;
	}
}

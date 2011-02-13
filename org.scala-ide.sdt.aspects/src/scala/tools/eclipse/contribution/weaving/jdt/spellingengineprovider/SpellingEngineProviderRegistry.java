/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.spellingengineprovider;

import scala.tools.eclipse.contribution.weaving.jdt.util.AbstractProviderRegistry;

public class SpellingEngineProviderRegistry extends AbstractProviderRegistry<ISpellingEngineProvider> {

  public static String SPELLING_ENGINE_PROVIDER_EXTENSION_POINT = "org.scala-ide.sdt.aspects.spellingengineprovider"; //$NON-NLS-1$
  
	private static final SpellingEngineProviderRegistry INSTANCE = new SpellingEngineProviderRegistry();

	public static SpellingEngineProviderRegistry getInstance() {
		return INSTANCE;
	}

	@Override
	protected String getExtensionPointId() {
		return SPELLING_ENGINE_PROVIDER_EXTENSION_POINT; //$NON-NLS-1$
	}
}

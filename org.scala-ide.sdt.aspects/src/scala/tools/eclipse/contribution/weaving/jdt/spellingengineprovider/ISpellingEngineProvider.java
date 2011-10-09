/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.spellingengineprovider;

import org.eclipse.jdt.internal.ui.text.spelling.SpellingEngine;

public interface ISpellingEngineProvider {
	public SpellingEngine getScalaSpellingEngine();
}

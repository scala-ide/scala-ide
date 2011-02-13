/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.spellingengineprovider;

import org.eclipse.ui.texteditor.spelling.ISpellingEngine;

public interface ISpellingEngineProvider {
	public ISpellingEngine getScalaSpellingEngine();
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

public interface IScalaWordFinder {
  public IRegion getWord(IDocument document, int offset);
}

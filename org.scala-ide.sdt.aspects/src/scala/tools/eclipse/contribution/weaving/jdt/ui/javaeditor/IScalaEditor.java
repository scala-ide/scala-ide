/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jface.text.rules.IPartitionTokenScanner;

public interface IScalaEditor {
  public IPartitionTokenScanner getPartitionScanner();
}

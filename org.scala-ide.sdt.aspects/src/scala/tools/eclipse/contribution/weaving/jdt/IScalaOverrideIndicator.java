/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

public interface IScalaOverrideIndicator {
  public boolean isOverwrite();
  public void open();
}

/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

public interface IScalaOverrideIndicator {
  public boolean isOverwriteIndicator();
  public void open();
}

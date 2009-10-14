/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

import org.eclipse.jface.resource.ImageDescriptor;

public interface IScalaElement {
  public ImageDescriptor getImageDescriptor();
  public String getLabelText(long flags);
}

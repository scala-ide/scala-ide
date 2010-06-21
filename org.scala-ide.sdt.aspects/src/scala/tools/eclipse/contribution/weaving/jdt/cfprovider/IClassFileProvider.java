/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.PackageFragment;

public interface IClassFileProvider {
  public ClassFile create(byte[] contents, PackageFragment parent, String name);
}

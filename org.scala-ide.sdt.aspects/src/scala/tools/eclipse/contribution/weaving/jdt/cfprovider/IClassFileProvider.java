/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.PackageFragment;

@SuppressWarnings("restriction")
public interface IClassFileProvider {
  public ClassFile create(byte[] contents, PackageFragment parent, String name);
  
  /** Is this classfile interesting for this provider? Gives a fast-path when the
   *  provider is not interested in a classfile (for instance, when the file does not
   *  belongs to a project with the desired nature). Answering 'false' saves the
   *  costly operation of loading the bytes in memory (needed for the call to 'create').
   * 
   * @param file the classfile under inspection
   * @return false if the file is definitely not interesting for this class file provider.
   */
  public boolean isInteresting(IClassFile file);
}

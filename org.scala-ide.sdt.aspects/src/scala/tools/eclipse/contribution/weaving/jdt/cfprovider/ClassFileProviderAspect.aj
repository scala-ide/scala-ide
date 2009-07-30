/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.cfprovider;

import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.PackageFragment;

import scala.tools.eclipse.contribution.weaving.jdt.ScalaJDTWeavingPlugin;

public aspect ClassFileProviderAspect {

  pointcut classFileCreations(PackageFragment parent, String name) : 
    call(protected ClassFile.new(PackageFragment, String)) &&
    within(org.eclipse.jdt..*) &&
    args(parent, name);

  ClassFile around(PackageFragment parent, String name) : 
    classFileCreations(parent, name) {

    ClassFile javaClassFile = proceed(parent, name); 
    if (javaClassFile.exists())
      for (IClassFileProvider provider : ClassFileProviderRegistry.getInstance().getProviders()) {
        try {
          ClassFile cf = provider.create(javaClassFile.getBytes(), parent, name);
          if (cf != null)
            return cf;
        } catch (Throwable t) {
          ScalaJDTWeavingPlugin.logException(t);
        }
      }
    
    return javaClassFile;
  }
}

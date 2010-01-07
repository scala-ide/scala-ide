/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.launching;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.launching.JavaLaunchableTester;

@SuppressWarnings("restriction")
public privileged aspect JavaLaunchableTesterAspect {
  pointcut hasMain(JavaLaunchableTester jlt, IJavaElement element) :
    execution(boolean JavaLaunchableTester.hasMain(IJavaElement)) &&
    target(jlt) &&
    args(element);
  
  boolean around(JavaLaunchableTester jlt, IJavaElement element) :
    hasMain(jlt, element) {
    try {
      IType type = jlt.getType(element);
      if(type != null && type.exists()) {
        if(jlt.hasMainMethod(type)) {
          return true;
        }
        //failed to find in public type, check static inner types
        IJavaElement[] children = type.getChildren();
        for(int i = 0; i < children.length; i++) {
          type = jlt.getType(children[i]);
          if(type != null && jlt.hasMainInChildren(type)) {
            return true;
          }
        }
      }
    }
    catch (JavaModelException e) {}
    catch (CoreException ce){}
    return false;
  }
}

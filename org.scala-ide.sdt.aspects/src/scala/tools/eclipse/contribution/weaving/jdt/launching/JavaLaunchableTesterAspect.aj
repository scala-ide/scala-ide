/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.launching;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.launching.JavaLaunchableTester;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;

@SuppressWarnings("restriction")
public privileged aspect JavaLaunchableTesterAspect {
  pointcut launchTesters(IJavaElement element, String name) :
    execution(boolean JavaLaunchableTester.hasSuperclass(IJavaElement, String)) && args(element, name);

  pointcut hasMain(IJavaElement element) :
    execution(boolean JavaLaunchableTester.hasMain(IJavaElement)) && args(element);

  boolean around(IJavaElement element) :
    hasMain(element) {
    if (element instanceof IScalaElement) {
      return false;
    } else
      return proceed(element);
  }

  boolean around(IJavaElement element, String name) :
    launchTesters(element, name) {
    if (element instanceof IScalaElement) {
      return false;
    } else
      return proceed(element, name);
  }
}

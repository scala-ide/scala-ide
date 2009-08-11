/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.OverrideIndicatorLabelDecorator;

@SuppressWarnings("restriction")
public privileged aspect ScalaOverrideLabelAspect {
  pointcut getOverrideIndicators(IMethod method) :
    args (method) &&
	  (execution(int OverrideIndicatorLabelDecorator.getOverrideIndicators(IMethod)));

  int around(IMethod method) throws JavaModelException : getOverrideIndicators(method) {
    if (method instanceof IMethodOverrideInfo) {
      IType type= method.getDeclaringType();

      MethodOverrideTester methodOverrideTester= SuperTypeHierarchyCache.getMethodOverrideTester(type);
      IMethod defining= methodOverrideTester.findOverriddenMethod(method, true);
      if (defining != null) {
        if (JdtFlags.isAbstract(defining)) {
          return JavaElementImageDescriptor.IMPLEMENTS;
        } else {
          return JavaElementImageDescriptor.OVERRIDES;
        }
      }
      return 0;
    } 
    else
      return proceed(method);
  }
}

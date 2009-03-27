/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.ui.JavaElementImageDescriptor;
import org.eclipse.jdt.ui.OverrideIndicatorLabelDecorator;


public privileged aspect ScalaOverrideLabelAspect {
  pointcut getOverrideIndicators(IMethod method) :
    args (method) &&
	  (execution(int OverrideIndicatorLabelDecorator.getOverrideIndicators(IMethod)));

  int around(IMethod method) : getOverrideIndicators(method) {
    if (method instanceof IMethodOverrideInfo) {
      return (((IMethodOverrideInfo) method).isOverride()) ? JavaElementImageDescriptor.OVERRIDES : 0;
    } 
    else
      return proceed(method);
  }
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.internal.ui.text.java.JavaMethodCompletionProposal;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompletionProposal;

@SuppressWarnings("restriction")
public privileged aspect CompletionAspect {
  pointcut hasArgumentList(JavaMethodCompletionProposal jmcp) :
    execution(boolean hasArgumentList()) &&
    target(jmcp);
  
  boolean around(JavaMethodCompletionProposal jmcp) :
    hasArgumentList(jmcp) {
    if ((jmcp.fProposal instanceof IScalaCompletionProposal) && ((IScalaCompletionProposal)jmcp.fProposal).suppressArgList())
      return false;
    else
      return proceed(jmcp);
  }
}

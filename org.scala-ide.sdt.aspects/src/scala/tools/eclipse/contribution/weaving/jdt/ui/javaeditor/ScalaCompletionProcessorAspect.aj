/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompletionProcessor;

@SuppressWarnings("restriction")
public privileged aspect ScalaCompletionProcessorAspect {
	pointcut getCompletionProposalAutoActivationCharactersPoint() :
		target(IScalaCompletionProcessor) &&
		execution(* org.eclipse.jdt.internal.ui.text.java.ContentAssistProcessor.getCompletionProposalAutoActivationCharacters()) ;
  
  char[] around() : getCompletionProposalAutoActivationCharactersPoint() {
    return new char[]{'.',':'};
  }
}

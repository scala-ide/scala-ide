/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.text.java;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.text.java.ContentAssistProcessor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jface.text.ITextViewer;

@SuppressWarnings("restriction")
public aspect ScalaCompletionProcessorAspect {
  pointcut collectProposals(ITextViewer viewer, int offset, IProgressMonitor monitor, ContentAssistInvocationContext context) :
    args(viewer, offset, monitor, context) &&
    execution(List ContentAssistProcessor.collectProposals(ITextViewer, int, IProgressMonitor, ContentAssistInvocationContext));

  List around(ScalaCompletionProcessor processor, ITextViewer viewer, int offset, IProgressMonitor monitor, ContentAssistInvocationContext context) :
    collectProposals(viewer, offset, monitor, context) && target(processor) {
    return processor.collectProposals0(viewer, offset, monitor, context);
  }
}

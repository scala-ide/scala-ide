/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.text.java;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.ui.IEditorPart;

@SuppressWarnings("restriction")
public abstract class ScalaCompletionProcessorStub extends JavaCompletionProcessor {

  public ScalaCompletionProcessorStub(IEditorPart editor, ContentAssistant assistant, String partition) {
    super(editor, assistant, partition);
  }

  public abstract List collectProposals0(ITextViewer viewer, int offset, IProgressMonitor monitor, ContentAssistInvocationContext context);
}

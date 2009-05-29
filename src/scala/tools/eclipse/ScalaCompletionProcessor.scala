/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.{ util => ju }

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.ui.text.java.{ JavaCompletionProcessor, JavaCompletionProposal }
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ContentAssistant
import org.eclipse.ui.IEditorPart

import scala.tools.eclipse.contribution.weaving.jdt.ui.text.java.ScalaCompletionProcessorStub

class ScalaCompletionProcessor(editor : IEditorPart, assistant : ContentAssistant, partition : String) extends ScalaCompletionProcessorStub(editor, assistant, partition) {
  override def collectProposals0(viewer : ITextViewer, offset : Int, monitor : IProgressMonitor, context : ContentAssistInvocationContext) : ju.List[_] = {
    val result = new ju.ArrayList[JavaCompletionProposal]
    result.add(new JavaCompletionProposal("Not yet implemented", offset, 0, null, null, 1, false))
    result
  }
}

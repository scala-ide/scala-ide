package org.scalaide.ui.internal.templates

import java.util.Collections
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import java.util.Arrays
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer
import org.scalaide.core.ScalaPlugin

// Default ctor to make it instantiable via the extension mechanism.
class TemplateCompletionProposalComputer extends IJavaCompletionProposalComputer {

    /** The wrapped processor. */
    private
    val _processor = new ScalaTemplateCompletionProcessor(ScalaPlugin.plugin.templateManager)

    /*
     * @see org.eclipse.jface.text.contentassist.ICompletionProposalComputer#computeCompletionProposals(org.eclipse.jface.text.contentassist.TextContentAssistInvocationContext, org.eclipse.core.runtime.IProgressMonitor)
     */
    def computeCompletionProposals(context : ContentAssistInvocationContext,  monitor : IProgressMonitor) : java.util.List[ICompletionProposal]= {
      _processor.computeCompletionProposals(context.getViewer(), context.getInvocationOffset()) match {
        case null => Collections.EMPTY_LIST.asInstanceOf[java.util.List[ICompletionProposal]]
        case a => Arrays.asList(a : _*)
      }
    }

    /*
     * @see org.eclipse.jface.text.contentassist.ICompletionProposalComputer#computeContextInformation(org.eclipse.jface.text.contentassist.TextContentAssistInvocationContext, org.eclipse.core.runtime.IProgressMonitor)
     */
    def computeContextInformation(context : ContentAssistInvocationContext, monitor : IProgressMonitor) : java.util.List[IContextInformation] = {
      _processor.computeContextInformation(context.getViewer(), context.getInvocationOffset()) match {
        case null => Collections.EMPTY_LIST.asInstanceOf[java.util.List[IContextInformation]]
        case a => Arrays.asList(a : _*)
      }
    }

    /*
     * @see org.eclipse.jface.text.contentassist.ICompletionProposalComputer#getErrorMessage()
     */
    def getErrorMessage() = _processor.getErrorMessage()

    /*
     * @see org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer#sessionStarted()
     */
    def sessionStarted() {}

    /*
     * @see org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer#sessionEnded()
     */
    def sessionEnded() {}
}

package scala.tools.eclipse.text.scala
import java.util.Collections
import java.util.List
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer

import org.eclipse.core.runtime.IProgressMonitor

import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.contentassist.CompletionProposal

import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
class ScalaCompletionProposalComputer extends IJavaCompletionProposalComputer {
	
	override def sessionStarted(){}
 
	override def computeCompletionProposals(context: ContentAssistInvocationContext, monitor: IProgressMonitor): List[ICompletionProposal] = {
        val ret = new java.util.ArrayList[ICompletionProposal];        val activationChar = context.getDocument.getChar(context.getInvocationOffset-1);        if (activationChar.equals(':'))   {          val scu = context.asInstanceOf[JavaContentAssistInvocationContext].getCompilationUnit().asInstanceOf[ScalaCompilationUnit];          val p = ScalaTypeAutoCompletionProposalManager.getProposalFor(scu);          if (!p.getDisplayString.equals("<error>")) { ret.add(p); };        };		return ret;
	}
 
	override def computeContextInformation(context: ContentAssistInvocationContext, monitor: IProgressMonitor): List[IContextInformation] = {
		return Collections.EMPTY_LIST.asInstanceOf[java.util.List[IContextInformation]]
	} 
	
	override def getErrorMessage(): String = {return ""}
 
	override def sessionEnded(){}

}
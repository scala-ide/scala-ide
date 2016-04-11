package org.scalaide.ui.internal.completion

import org.scalaide.core.completion.ScalaCompletions
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.ScalaWordFinder
import org.scalaide.ui.completion.ScalaCompletionProposal

class ScalaCompletionProposalComputer extends ScalaCompletions with IJavaCompletionProposalComputer {
  override def sessionStarted(): Unit = {}
  override def sessionEnded(): Unit = {}
  override def getErrorMessage() = null

  override def computeContextInformation(context : ContentAssistInvocationContext,
      monitor : IProgressMonitor): java.util.List[IContextInformation] = {
    // Currently not supported
    java.util.Collections.emptyList()
  }

  override def computeCompletionProposals(context : ContentAssistInvocationContext,
         monitor : IProgressMonitor): java.util.List[ICompletionProposal] = {
    import java.util.Collections.{ emptyList => javaEmptyList }

    val position = context.getInvocationOffset()
    context match {
      case jc : JavaContentAssistInvocationContext => jc.getCompilationUnit match {
        case scu : ScalaCompilationUnit =>
          findCompletions(position, context, scu)
        case _ => javaEmptyList()
      }
      case _ => javaEmptyList()
    }
  }

  private def findCompletions(position: Int, context: ContentAssistInvocationContext, scu: ScalaCompilationUnit): java.util.List[ICompletionProposal] = {
    val chars = context.getDocument
    val region = ScalaWordFinder.findCompletionPoint(chars, position)

    val res = getCompletions(region, position, scu)

    import collection.JavaConverters._

    res.map(ScalaCompletionProposal(_): ICompletionProposal).asJava
  }
}

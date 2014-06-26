package org.scalaide.ui.internal.completion

import scala.collection.mutable
import org.scalaide.core.completion.ScalaCompletions
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.contentassist.IContextInformationExtension
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.compiler.CharOperation
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.swt.graphics.Image
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jface.text.IDocument
import scala.tools.nsc.symtab.Flags
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.util.internal.ScalaWordFinder

class ScalaCompletionProposalComputer extends ScalaCompletions with IJavaCompletionProposalComputer {
  def sessionStarted() {}
  def sessionEnded() {}
  def getErrorMessage() = null


  def computeContextInformation(context : ContentAssistInvocationContext,
      monitor : IProgressMonitor) : java.util.List[IContextInformation] = {
    // Currently not supported
    java.util.Collections.emptyList()
  }

  def computeCompletionProposals(context : ContentAssistInvocationContext,
         monitor : IProgressMonitor) : java.util.List[ICompletionProposal] = {
    import java.util.Collections.{ emptyList => javaEmptyList }

    val position = context.getInvocationOffset()
    context match {
      case jc : JavaContentAssistInvocationContext => jc.getCompilationUnit match {
        case scu : ScalaCompilationUnit =>
          scu.withSourceFile { findCompletions(position, context, scu) } getOrElse (javaEmptyList())
        case _ => javaEmptyList()
      }
      case _ => javaEmptyList()
    }
  }

  private def findCompletions(position: Int, context: ContentAssistInvocationContext, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler): java.util.List[ICompletionProposal] = {
    val chars = context.getDocument.get.toCharArray
    val region = ScalaWordFinder.findCompletionPoint(chars, position)

    val res = findCompletions(region)(position, scu)(sourceFile, compiler)

    import collection.JavaConversions._

    res.map(new ScalaCompletionProposal(_)): java.util.List[ICompletionProposal]
  }
}

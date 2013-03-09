/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package ui

import scala.collection.mutable
import completion.ScalaCompletions

import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.contentassist.
             {ICompletionProposal, ICompletionProposalExtension,
              IContextInformation, IContextInformationExtension}
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.compiler.CharOperation
import org.eclipse.jdt.ui.text.java.{IJavaCompletionProposalComputer,
                                     ContentAssistInvocationContext,
                                     JavaContentAssistInvocationContext,
                                     IJavaCompletionProposal}
import org.eclipse.swt.graphics.Image
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jface.text.IDocument
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.SourceFile
import javaelements.ScalaCompilationUnit

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
          scu.withSourceFile { findCompletions(position, context, scu) } (javaEmptyList())
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

    // COMPAT: 2.8 compatiblity. backwards compatible: this compiles both with 2.9 and 2.8
    import collection.JavaConversions._

    res.map(ScalaCompletionProposal(context.getViewer().getSelectionProvider)): java.util.List[ICompletionProposal]
  }
}

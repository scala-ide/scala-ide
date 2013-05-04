/**
 *
 */
package scala.tools.eclipse.templates

import java.util.Collections
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import java.util.Arrays
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer
import org.eclipse.jdt.internal.ui.text.java.AbstractTemplateCompletionProposalComputer
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.TemplateContextType;

import org.eclipse.jdt.internal.corext.template.java.JavaContextType;
import org.eclipse.jdt.internal.corext.template.java.JavaDocContextType;

import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jdt.internal.ui.text.template.contentassist.TemplateEngine;

//Default ctor to make it instantiatable via the extension mechanism.
//TODO clean up import
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

//Take inspiration from :
// * http://help.eclipse.org/galileo/index.jsp?topic=/org.eclipse.jdt.doc.isv/guide/jdt_api_codeassist.htm
// * http://eclipseone.wordpress.com/2009/11/29/how-to-tweak-eclipse-templates-to-suit-you/
// * org.eclipse.jdt.internal.ui.text.java http://www.java2s.com/Open-Source/Java-Document/IDE-Eclipse/jdt/org.eclipse.jdt.internal.ui.text.java.htm
// * org.eclipse.jdt.internal.ui.text.template.contentassist http://www.java2s.com/Open-Source/Java-Document/IDE-Eclipse/jdt/org.eclipse.jdt.internal.ui.text.template.contentassist.htm
// * http://blog.jcake.com/2009/11/29/easy-sharing-of-eclipse-templates/ but need that scala templates be listed into Template View before !

//TODO try to do find if it's possible to extends AbstractTemplateCompletionProposalComputer (without fully rewrite TemplateEngine (use JavaPlugin.getTemplateStore)
///**
// * Computer computing template proposals for Scala (and Javadoc) context type.
// * @see http://www.java2s.com/Open-Source/Java-Document/IDE-Eclipse/jdt/org/eclipse/jdt/internal/ui/text/java/TemplateCompletionProposalComputer.java.htm
// */
//class TemplateCompletionProposalComputer extends  AbstractTemplateCompletionProposalComputer {
//
//    private
//    val _scalaTemplateEngine = {
//      val tm = ScalaPlugin.plugin.templateManager
//      val contextType = tm.contextTypeRegistry.getContextType(tm.CONTEXT_TYPE)
//      new TemplateEngine(contextType);
//    }
//
//    /* (non-Javadoc)
//     * @see org.eclipse.jdt.internal.ui.text.java.TemplateCompletionProposalComputer#computeCompletionEngine(org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext)
//     */
//    protected
//    def computeCompletionEngine(context : JavaContentAssistInvocationContext) : TemplateEngine = {
//      try {
//        TextUtilities.getContentType(context.getDocument(), IJavaPartitions.JAVA_PARTITIONING, context.getInvocationOffset(), true) match {
//          case IJavaPartitions.JAVA_DOC => null // TODO
//          case _ => _scalaTemplateEngine
//        }
//      } catch {
//        case e : BadLocationException => null
//      }
//    }
//
//}

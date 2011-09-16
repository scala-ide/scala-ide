package scala.tools.eclipse.completion

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer

import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import java.util.Collections.{ emptyList => javaEmptyList }
import org.eclipse.jdt.core._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.text.Document
import scala.tools.eclipse.ScalaImages
import scala.tools.eclipse.ui.ScalaCompletionProposal

/** A completion proposal for Java sources. This adds mixed-in concrete members to scope
 *  completions in Java.
 *  
 *  Although the ScalaStructureBuilder reports traits and classes correctly, the Java 
 *  compleion proposal is confused by concrete trait members. Specifically, given 
 *  a Scala trait T with a concrete method 'foo', and a Scala class C that extends T,
 *  all Java classes that extend C won't see 'foo' in the completion proposal. The reason
 *  is that the Java completion proposal won't collect inherited /interface/ methods from
 *  concrete types, considering that all interface methods have to be implemented in one
 *  of the concrete classes seen so far. Obviously, this doesn't hold for concrete
 *  trait members.
 *  
 *  This class only proposes concrete methods coming from Scala traits, when the completion
 *  is a 'scope' completion (no prefix).
 * 
 */
class ScalaJavaCompletionProposalComputer extends IJavaCompletionProposalComputer {
  def sessionStarted() {}
  def sessionEnded() {}
  def getErrorMessage() = null

  def computeCompletionProposals(context: ContentAssistInvocationContext, monitor: IProgressMonitor): java.util.List[_] = {
    context match {
      case jc: JavaContentAssistInvocationContext => 
        if (ScalaPlugin.plugin.isScalaProject(jc.getProject()))
          jc.getCompilationUnit match {
          case scu: ScalaCompilationUnit => javaEmptyList()
          case _                         => mixedInCompletions(jc)
        } else
          javaEmptyList()
      case _ => javaEmptyList()
    }
  }

  /** Only propose completions on empty prefix, or a prefix that starts with
   *  'this' and has at most one dot.
   *  
   *  @note This should probably use the JDT core ASTParser to properly identify
   *        selections on 'this', but it seemed overkill at this point.
   */
  private def shouldProposeCompletion(line: String) = {
    val trimmed = line.trim
    ((trimmed.startsWith("this") && trimmed.split(".").size <= 2)
        || trimmed.indexOf('.') == -1)
  }
  
  import scala.collection.JavaConversions._
  
  /** Return completion proposals for mixed-in methods.
   */
  def mixedInCompletions(jc: JavaContentAssistInvocationContext): java.util.List[_] = {
    val coreContext = jc.getCoreContext()
    val elem = if (coreContext.isExtended) coreContext.getEnclosingElement else jc.getCompilationUnit().getElementAt(jc.getInvocationOffset)
    val doc = jc.getDocument
    val region = doc.getLineInformationOfOffset(coreContext.getOffset)
    val line = doc.get(region.getOffset, region.getLength)
    
    val prefix = line.lastIndexOf('.') match {
      case -1 => line.trim
      case pos => line.substring(pos + 1)
    }
    val start = jc.getInvocationOffset - prefix.length
    
    if ((elem ne null) && shouldProposeCompletion(line)) {
      val completionProposals = elem match {
        case m: IMember =>
          val tpe = (if (m.getElementType() == IJavaElement.TYPE) m.asInstanceOf[IType] else m.getDeclaringType()).getFullyQualifiedName()
  
          val prj = ScalaPlugin.plugin.getScalaProject(m.getJavaProject.getProject)
          prj.withPresentationCompiler { compiler =>
            import compiler._
  
            def mixedInMethod(sym: Symbol): Boolean =
              (sym.isSourceMethod &&
                  !sym.isDeferred && 
                  sym.owner.isTrait && 
                  !sym.isConstructor && 
                  !sym.isPrivate)
  
            compiler.askOption { () =>
              val currentClass = definitions.getClass(tpe.toTypeName)
              val proposals = currentClass.info.members.filter(mixedInMethod)

              for (sym <- proposals if sym.name.startsWith(prefix)) yield {
                val prop = compiler.mkCompletionProposal(start, sym = sym, tpe = sym.info, inherited = true, viaView = NoSymbol)
                new ScalaCompletionProposal(prop, jc.getViewer.getSelectionProvider)
              }
            }.getOrElse(Nil)
          } (Nil)
        case _ => Nil
      }
      completionProposals: java.util.List[_]
    } else
      javaEmptyList()
  }
  
  def computeContextInformation(context: ContentAssistInvocationContext, monitor: IProgressMonitor) = 
    javaEmptyList()
}

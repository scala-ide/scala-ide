package scala.tools.eclipse.text.scala;

import java.io.PrintWriter
import java.io.StringWriter
import java.util.WeakHashMap
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.Image

import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.DocumentEvent;
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.eclipse.internal.logging.Tracer

class ScalaTypeAutoCompletionProposal(scu: ScalaCompilationUnit, doc: IDocument) extends IJavaCompletionProposal with IDocumentListener {

  var currentTyp: String = _;
  var offset: Int = _;

  override def documentAboutToBeChanged(event: DocumentEvent) {
    if (event.getText.equals(":")) {
      offset = event.getOffset
      scu.withSourceFile { (sourceFile, compiler) =>
        val pos = compiler.rangePos(sourceFile, offset - 1, offset - 1, offset)
        val typed = new compiler.Response[compiler.Tree]
        compiler.askTypeAt(pos, typed)
        currentTyp = typed.get.left.toOption match {
          case Some(tree) => {
            tree match {
              case v: compiler.ValDef => v.tpt.toString
              case d: compiler.DefDef => d.tpt.toString
              case _ => "<error>"
            }
          }
          case None => "<None>"
        }
      }
      Tracer.println(currentTyp)
    }
  }

  override def documentChanged(event: DocumentEvent) {}

  def apply(document: IDocument): Unit = {
    document.replace(offset + 1, 0, " " + currentTyp)
  }

  def getDisplayString(): String = currentTyp

  def getImage(): Image = org.eclipse.jdt.internal.ui.JavaPluginImages.get(org.eclipse.jdt.internal.ui.JavaPluginImages.IMG_OBJS_CLASS)

  def getContextInformation(): IContextInformation = { null }

  def getSelection(document: IDocument): Point = { null }

  def getAdditionalProposalInfo(): String = { null }

  def getRelevance() = Integer.MAX_VALUE //temporary hack for jdt 

}

object ScalaTypeAutoCompletionProposalManager {

  val internalInstanceMap = new WeakHashMap[ScalaCompilationUnit, ScalaTypeAutoCompletionProposal];

  private def createProposal(scu: ScalaCompilationUnit) {
    val bufferManager = org.eclipse.core.filebuffers.FileBuffers.getTextFileBufferManager();
    val path = scu.getPath();
    try {
      bufferManager.connect(path, null)
      val doc = bufferManager.getTextFileBuffer(path).getDocument(); // work with document}
      val instance = new ScalaTypeAutoCompletionProposal(scu, doc)
      internalInstanceMap.put(scu, instance)
    } finally {
      bufferManager.disconnect(path, null)
    }

  }

  def getProposalFor(scu: ScalaCompilationUnit): ScalaTypeAutoCompletionProposal = {
    val ret = internalInstanceMap.get(scu)
    if (ret eq null) { createProposal(scu) }
    internalInstanceMap.get(scu)
  }
}
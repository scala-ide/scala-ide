package org.scalaide.core.internal.quickassist

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor
import org.eclipse.jface.text.quickassist.QuickAssistAssistant
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.IEditorInput
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.quickassist.AssistLocation
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.ui.editor.ScalaEditorAnnotation
import org.scalaide.util.eclipse.EditorUtils

/**
 * Referenced in the plugins.xml. This handler gets called when an user asks for
 * quick assists.
 */
final class QuickAssistHandler extends AbstractHandler {

  override def execute(event: ExecutionEvent): AnyRef = {
    EditorUtils.doWithCurrentEditor { editor =>
      val input = editor.getEditorInput
      val p = new QuickAssistProcessor(input, event.getCommand.getId)
      val a = new QuickAssistAssistant
      a.setQuickAssistProcessor(p)
      a.install(editor.getViewer)
      a.showPossibleQuickAssists()
    }
    null
  }

}

/**
 * This proposal should be shown when no other proposals are found. If no
 * proposals would be shown, the user wouldn't see a quick assist window at all.
 */
object NoProposals extends BasicCompletionProposal(0, "No suggestions available") {
  override def apply(doc: IDocument): Unit = ()
}

object QuickAssistProcessor {
  /**
   * All available quick assists.
   */
  val QuickAssists: Seq[QuickAssistMapping] = QuickAssistMapping.mappings

  /**
   * The ID that needs to be used when all available quick assists should be
   * executed in the companion class.
   */
  final val DefaultId = "org.scalaide.core.quickassist"
}

/**
 * A quick assist processor that executes quick assists that are registered
 * for the Scala editor. Always returns at least one element when
 * `computeQuickAssistProposals` is executed. This rule is ensured by returning
 * an array that contains [[NoProposals]] when no proposals are found.
 *
 * @param input
 *        The editor where the quick assist feature should be invoked on.
 * @param id
 *        Represents the ID that belongs to a quick assist. The corresponding
 *        quick assist is executed by this processor. If this ID is the same as
 *        [[QuickAssistProcessor.DefaultId]] all available quick assists are
 *        executed. If the ID doesn't exist no quick assists are executed.
 */
final class QuickAssistProcessor(input: IEditorInput, id: String) extends IQuickAssistProcessor {
  import QuickAssistProcessor._

  override def computeQuickAssistProposals(ctx: IQuickAssistInvocationContext): Array[ICompletionProposal] = {
    def problems = {
      import collection.JavaConverters._
      val model = JavaUI.getDocumentProvider.getAnnotationModel(input)
      val iter = model.getAnnotationIterator.asScala

      (iter foldLeft IndexedSeq[AssistLocation]()) {
        case (ps, a: Annotation) if a.isInstanceOf[ScalaEditorAnnotation] || a.isInstanceOf[IJavaAnnotation] =>
          val pos = model.getPosition(a)
          if (isInside(ctx.getOffset, pos.offset, pos.offset+pos.length))
            ps :+ AssistLocation(pos.offset, pos.length, a)
          else
            ps
        case (ps, _) =>
          ps
      }
    }

    val assists =
      if (id == DefaultId)
        QuickAssists
      else
        QuickAssists.find(_.id == id).toSeq

    JavaUI.getWorkingCopyManager.getWorkingCopy(input) match {
      case ssf: ScalaSourceFile if assists.nonEmpty =>
        val ictx = InvocationContext(ssf, ctx.getOffset, ctx.getLength, problems)
        val proposals = assists flatMap (_ withInstance (_ compute ictx))
        val sorted = proposals.flatten.sortBy(-_.getRelevance())

        if (sorted.isEmpty)
          Array(NoProposals)
        else
          sorted.toArray

      case _ =>
        Array(NoProposals)
    }
  }

  override def canAssist(ctx: IQuickAssistInvocationContext): Boolean = true
  override def canFix(a: Annotation): Boolean = true

  override def getErrorMessage(): String = null

  private def isInside(offset: Int, start: Int, end: Int): Boolean =
    offset == start || offset == end || (offset > start && offset < end)
}

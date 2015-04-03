package org.scalaide.core.internal.quickassist

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor
import org.eclipse.jface.text.quickassist.QuickAssistAssistant
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.IEditorInput
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Features.NotSpecified
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
object NoProposals extends BasicCompletionProposal(NotSpecified, 0, "No suggestions available") {
  override def applyProposal(doc: IDocument): Unit = ()
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
    /**
     * Searches for annotations whose position intersects with the cursor
     * position and maps them to `AssistLocation`. In case there exists a
     * problem annotation in the same line where also the cursor is located but
     * it does not intersect with the cursor position, it is separately returned.
     *
     * The precedence rules to show quick assists (based on these two return
     * values) are as follows:
     *
     * 1. Show quick assists considering the cursor position but only if a
     *    problem annotation exists at the cursor position.
     * 2. Show quick assists considering the problem position of the same line
     *    if it exists.
     * 3. Show quick assists considering the cursor position.
     *
     * Beside from that, the cursor needs to be moved to the position of the
     * problem annotation of the same line unless it is already at the position
     * of the annotation - in this case it should not move.
     */
    def assistLocations = {
      import collection.JavaConverters._
      val model = ScalaPlugin().documentProvider.getAnnotationModel(input)
      val iter = model.getAnnotationIterator.asScala
      val d = ctx.getSourceViewer.getDocument
      val lineOfInvocation = d.getLineOfOffset(ctx.getOffset)

      type Assists = IndexedSeq[AssistLocation]
      type FirstProblem = Option[AssistLocation]

      def loop(as: Assists, fp: FirstProblem): (Assists, FirstProblem) =
        if (iter.isEmpty)
          (as, fp)
        else iter.next() match {
          case a: Annotation if a.isInstanceOf[ScalaEditorAnnotation] || a.isInstanceOf[IJavaAnnotation] =>
            val (start, end) = {
              val p = model.getPosition(a)
              (p.offset, p.offset+p.length)
            }

            def isOffsetInsidePos(offset: Int): Boolean =
              offset == start || offset == end || (offset > start && offset < end)

            def isPosInsideRange(rStart: Int, rEnd: Int): Boolean =
              start >= rStart && end <= rEnd

            def posNotYetFound =
              as.forall(a => !isPosInsideRange(a.offset, a.offset+a.length))

            def isProblemInLine(a: Annotation, offset: Int, end: Int) =
              a.isInstanceOf[ProblemAnnotation] && lineOfInvocation == d.getLineOfOffset(offset)

            if (isOffsetInsidePos(ctx.getOffset) && posNotYetFound)
              loop(as :+ AssistLocation(start, end-start, a), fp)
            else if (fp.isEmpty && posNotYetFound && isProblemInLine(a, start, end))
              loop(as, Some(AssistLocation(start, end-start, a)))
            else
              loop(as, fp)
          case _ =>
            loop(as, fp)
        }

      loop(IndexedSeq(), None)
    }

    val ssf = IScalaPlugin().scalaCompilationUnit(input)
    val quickAssists =
      if (id == DefaultId)
        QuickAssists
      else
        QuickAssists.find(_.id == id).toSeq

    if (quickAssists.isEmpty || ssf.isEmpty)
      Array(NoProposals)
    else {
      val (locations, problemInLine) = assistLocations
      val problemAtCursor = locations.find(_.annotation.isInstanceOf[ProblemAnnotation])

      // ctx.getLength is always -1, we need to retrieve it manually
      def selLen = ctx.getSourceViewer.getSelectionProvider.getSelection match {
        case s: TextSelection => s.getLength
        case _                => 0
      }

      def cursorIctx = InvocationContext(ssf.get, ctx.getOffset, selLen, locations)
      def problemAtCursorIctx = problemAtCursor map (_ => cursorIctx)
      def problemIctx = problemInLine map (al => InvocationContext(ssf.get, al.offset, 0, locations :+ al))

      val ictx = problemAtCursorIctx orElse problemIctx getOrElse cursorIctx
      val proposals = quickAssists flatMap (_ withInstance (_ compute ictx))
      val sorted = proposals.flatten.sortBy(-_.getRelevance())

      EditorUtils.withCurrentEditor { e =>
        e.selectAndReveal(ictx.selectionStart, ictx.selectionLength)
        None
      }

      if (sorted.isEmpty)
        Array(NoProposals)
      else
        sorted.toArray
    }
  }

  override def canAssist(ctx: IQuickAssistInvocationContext): Boolean = true
  override def canFix(a: Annotation): Boolean = true
  override def getErrorMessage(): String = null
}

package scala.tools.eclipse.hyperlink.text.detector

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor

import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitConversionAnnotation
import scala.tools.eclipse.util.EditorUtils.getAnnotationsAtOffset

private class ImplicitHyperlinkDetector extends BaseHyperlinkDetector {

  override protected[detector] def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    findHyperlinkToImplicit(scu, currentSelection.getOffset, textEditor)

  /**
   * Check if an {{{ImplicitConversionAnnotation}}} at the given {{{offset}}} exists in the
   * editor's annotation model.
   *
   * @return the {{{IHyperlink}}} to the implicit declaration, if one exists.
   */
  // FIXME: I quite dislike the current implementation, for the following reasons:
  //        1) We go through all the editor's annotations to find if an implicit conversion is applied at the given {{{offset}}}.
  //        2) Because we use the editor's annotation model, this functionality cannot be tested in a UI-less environment.
  private def findHyperlinkToImplicit(scu: InteractiveCompilationUnit, offset: Int, editor: ITextEditor): List[IHyperlink] = {
    import scala.tools.eclipse.semantichighlighting.implicits.ImplicitConversionAnnotation
    import scala.tools.eclipse.util.EditorUtils.getAnnotationsAtOffset

    var hyperlinks = List[IHyperlink]()

    for ((ann, pos) <- getAnnotationsAtOffset(editor, offset)) ann match {
      case a: ImplicitConversionAnnotation if a.sourceLink.isDefined =>
        hyperlinks = a.sourceLink.get :: hyperlinks
      case _ => ()
    }

    hyperlinks
  }
}

object ImplicitHyperlinkDetector {
  def apply(): BaseHyperlinkDetector = new ImplicitHyperlinkDetector
}
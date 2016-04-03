package org.scalaide.core.internal.hyperlink

import scala.collection.mutable.ListBuffer

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor

private class ImplicitHyperlinkDetector extends BaseHyperlinkDetector {

  override protected[internal] def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    findHyperlinkToImplicit(currentSelection.getOffset, textEditor)

  /**
   * Check if an {{{ImplicitConversionAnnotation}}} at the given {{{offset}}} exists in the
   * editor's annotation model.
   *
   * @return the {{{IHyperlink}}} to the implicit declaration, if one exists.
   */
  // FIXME: I quite dislike the current implementation, for the following reasons:
  //        1) We go through all the editor's annotations to find if an implicit conversion is applied at the given {{{offset}}}.
  //        2) Because we use the editor's annotation model, this functionality cannot be tested in a UI-less environment.
  private def findHyperlinkToImplicit(offset: Int, editor: ITextEditor): List[IHyperlink] = {
    import org.scalaide.ui.internal.editor.decorators.implicits.ImplicitConversionAnnotation
    import org.scalaide.util.eclipse.EditorUtils

    val hyperlinks = ListBuffer[IHyperlink]()
    editor match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        for ((ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, offset)) ann match {
          case a: ImplicitConversionAnnotation =>
            hyperlinks ++= a.sourceLink(icuEditor)
          case _ => ()
        }
      case _ =>
    }

    hyperlinks.toList
  }
}

object ImplicitHyperlinkDetector {
  def apply(): BaseHyperlinkDetector = new ImplicitHyperlinkDetector
}

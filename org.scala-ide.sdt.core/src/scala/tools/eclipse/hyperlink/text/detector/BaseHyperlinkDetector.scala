package scala.tools.eclipse.hyperlink.text.detector

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

abstract class BaseHyperlinkDetector extends AbstractHyperlinkDetector {

  final def detectHyperlinks(viewer: ITextViewer, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    detectHyperlinks(textEditor, currentSelection, canShowMultipleHyperlinks)
  }

  final def detectHyperlinks(textEditor: ITextEditor, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    if (textEditor == null) null // can be null if generated through ScalaPreviewerFactory
    else
      EditorUtility.getEditorInputJavaElement(textEditor, false) match {
        case scu: ScalaCompilationUnit =>
          val hyperlinks = runDetectionStrategy(scu, textEditor, currentSelection)
          // I know you will be tempted to remove this, but don't do it, JDT expects null when no hyperlinks are found.
          if (hyperlinks.isEmpty) null 
          else hyperlinks.toArray

        case _ => null
      }
  }

  private[detector] def runDetectionStrategy(scu: ScalaCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink]
}
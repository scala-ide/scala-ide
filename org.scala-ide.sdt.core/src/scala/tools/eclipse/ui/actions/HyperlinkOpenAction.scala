package scala.tools.eclipse.ui.actions

import org.eclipse.jdt.ui.actions.OpenAction
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import scala.tools.eclipse.hyperlink.text.detector.BaseHyperlinkDetector
import scala.tools.eclipse.util.EditorUtils
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer

class HyperlinkOpenAction(detectionStrategy: BaseHyperlinkDetector, editor: JavaEditor) extends OpenAction(editor) {

  override def run() {
    withScalaCompilatioUnit {
      _.followReference(detectionStrategy, editor, getSelectionProvider.getSelection.asInstanceOf[ITextSelection])
    }
  }

  private def withScalaCompilatioUnit[T](f: ScalaCompilationUnit => T): Option[T] = {
    val inputJavaElement = EditorUtility.getEditorInputJavaElement(editor, false)
    Option(inputJavaElement) map (_.asInstanceOf[ScalaCompilationUnit]) map (f)
  }

  override def isEnabled: Boolean = getSelectionProvider.getSelection match {
    case textSelection: TextSelection =>
      val region = EditorUtils.textSelection2region(textSelection)
      detectionStrategy.detectHyperlinks(editor.getViewer, region, canShowMultipleHyperlinks = false) != null
    case _ => false
  }
}
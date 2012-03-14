package scala.tools.eclipse.ui.actions

import org.eclipse.jdt.ui.actions.OpenAction
import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor

class HyperlinkOpenAction(editor: JavaEditor) extends OpenAction(editor) {
  override def run() {
    val inputJavaElement = EditorUtility.getEditorInputJavaElement(editor, false)
    Option(inputJavaElement) map (_.asInstanceOf[ScalaCompilationUnit]) foreach { scu =>
      scu.followReference(editor, getSelectionProvider.getSelection.asInstanceOf[ITextSelection])
    }
  }
}
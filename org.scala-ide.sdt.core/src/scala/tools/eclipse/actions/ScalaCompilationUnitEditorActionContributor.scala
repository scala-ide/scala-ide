package scala.tools.eclipse.actions

import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.IEditorPart
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditorActionContributor
import scala.tools.eclipse.semicolon.ShowInferredSemicolonsAction

class ScalaCompilationUnitEditorActionContributor extends CompilationUnitEditorActionContributor {

  override def setActiveEditor(part: IEditorPart) {
    super.setActiveEditor(part)
    val action = getAction(part.asInstanceOf[ITextEditor], ShowInferredSemicolonsAction.ACTION_ID)
    getActionBars.setGlobalActionHandler(ShowInferredSemicolonsAction.ACTION_ID, action)
  }

}
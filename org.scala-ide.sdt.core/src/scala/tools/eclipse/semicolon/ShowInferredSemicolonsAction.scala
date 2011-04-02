package scala.tools.eclipse.semicolon

import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.ui.texteditor._
import java.util.ResourceBundle
import org.eclipse.core.runtime.Assert
import org.eclipse.jface.action.IAction
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IPainter
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jface.text.WhitespaceCharacterPainter

object ShowInferredSemicolonsAction {

  val PREFERENCE_KEY = "actions.showInferredSemicolons"

  val ACTION_ID = "showInferredSemicolonsAction"

  val ACTION_DEFINITION_ID = "scala.tools.eclipse.toggleShowInferredSemicolonsAction"

  def getBundle[T](implicit m: Manifest[T]) = ResourceBundle.getBundle(m.erasure.getName)

}

import ShowInferredSemicolonsAction._

class ShowInferredSemicolonsAction(prefix: String, editor: ITextEditor, preferenceStore: IPreferenceStore)
  extends TextEditorAction(getBundle[ShowInferredSemicolonsBundle], prefix, editor, IAction.AS_CHECK_BOX) {

  private var painterOpt: Option[IPainter] = _

  synchronizeWithPreference()

  override def run() {
    togglePainterState(isChecked)
    preferenceStore.setValue(PREFERENCE_KEY, isChecked)
  }

  override def update() {
    synchronizeWithPreference()
  }

  private def togglePainterState(newState: Boolean) =
    if (newState) installPainter() else uninstallPainter()

  private def installPainter() {
    val painter = new InferredSemicolonPainter(viewer)
    viewer.addPainter(painter)
    painterOpt = Some(painter)
  }

  private def uninstallPainter() {
    painterOpt foreach { painter =>
      viewer.removePainter(painter)
      painter.deactivate(true)
      painter.dispose()
    }
    painterOpt = None
  }

  private def synchronizeWithPreference() {
    val checked = preferenceStore.getBoolean(PREFERENCE_KEY)
    if (checked != isChecked) {
      setChecked(checked)
      togglePainterState(checked)
    }
  }

  private def textEditor = super.getTextEditor.asInstanceOf[ScalaSourceFileEditor]

  private def viewer = textEditor.sourceViewer

}
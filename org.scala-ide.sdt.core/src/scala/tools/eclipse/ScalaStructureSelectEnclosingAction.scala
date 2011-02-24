package scala.tools.eclipse

import org.eclipse.jdt.internal.corext.SourceRange
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions._
import org.eclipse.jface.action.Action
import org.eclipse.jface.text.ITextSelection

import scalariform.astselect.AstSelector
import scalariform.utils.Range

/**
 * A Scala-aware replacement for {@link org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectEnclosingAction}.
 */
class ScalaStructureSelectEnclosingAction(editor: ScalaSourceFileEditor, selectionHistory: SelectionHistory) extends Action {

  override def run() {
    val source = editor.getDocumentProvider.getDocument(editor.getEditorInput).get
    val selection = editor.getSelectionProvider.getSelection.asInstanceOf[ITextSelection]
    val selectionRange = Range(selection.getOffset, selection.getLength)
    for (Range(offset, length) <- AstSelector.expandSelection(source, selectionRange)) {
      selectionHistory.remember(new SourceRange(selection.getOffset, selection.getLength))
      try {
        selectionHistory.ignoreSelectionChanges()
        editor.selectAndReveal(offset, length)
      } finally {
        selectionHistory.listenToSelectionChanges()
      }
    }
  }
}

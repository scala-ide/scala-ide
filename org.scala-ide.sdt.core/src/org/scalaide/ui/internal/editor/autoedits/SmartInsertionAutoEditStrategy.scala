package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager.UndoSpec
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.text.edits.DeleteEdit
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.ui.IEditorPart
import org.scalaide.ui.internal.preferences.EditorPreferencePage

/** Non-UI logic for `SmartInsertionAutoEditStrategy` */
trait SmartInsertionLogic extends IAutoEditStrategy {

  def prefStore: IPreferenceStore

  /**
   * Registers a text edit that inserts one character into the document. The
   * edit is based on a `DocumentCommand` and the new caret position.
   */
  def registerTextEdit(cmd: DocumentCommand, cursorPos: Int): Unit

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand) = {
    val isSmartSemicolonInsertionEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_SMART_INSERTION_SEMICOLONS)

    def computeSemicolonInsertPosition(line: IRegion) = {
      val s = doc.get(line.getOffset(), line.getLength())
      val cursorPos = cmd.offset - line.getOffset()

      def trailingWsSize = s.reverse.takeWhile(Character.isWhitespace).size

      val i = s.indexOf("for")
      val noMoveNecessary = i >= 0 && i < cursorPos

      if (noMoveNecessary)
        None
      else
        Some(s.length()-trailingWsSize)
    }

    /**
     * Expects a function `f` that computes the relative insert position of a
     * char `c` to a given line. `f` needs to return `None` if if no more
     * accurate insert position than the current cursor position is found.
     */
    def hanldleSmartInsert(c: Char)(f: IRegion => Option[Int]) = {
      val l = doc.getLineInformationOfOffset(cmd.offset)
      def alreadyPresent(off: Int) = doc.getChar(l.getOffset()+off) == c

      f(l) foreach { posInLine =>
        if (!alreadyPresent(posInLine-1)) {
          val pos = posInLine + l.getOffset()
          registerTextEdit(cmd, pos)

          cmd.offset = pos
          cmd.length = 0
          cmd.caretOffset = pos
        }
      }
    }

    cmd.text match {
      case ";" if isSmartSemicolonInsertionEnabled =>
        hanldleSmartInsert(';')(computeSemicolonInsertPosition)
      case _ =>
    }
  }
}

class SmartInsertionAutoEditStrategy(editor: IEditorPart, val prefStore: IPreferenceStore)
    extends SmartInsertionLogic {

  override def registerTextEdit(cmd: DocumentCommand, cursorPos: Int) = {
    val delete = new DeleteEdit(cursorPos, 1)
    val insert = new ReplaceEdit(cmd.offset, cmd.length, cmd.text)
    val r = new Region(cmd.offset+cmd.text.length(), 0)
    val undoDelete = new UndoSpec(cursorPos+1, r, Array(delete, insert), 2, null)

    val m = editor.getAdapter(classOf[SmartBackspaceManager]).asInstanceOf[SmartBackspaceManager]
    m.register(undoDelete)
  }

}
package scala.tools.eclipse.ui

import scala.tools.eclipse.properties.EditorPreferencePage

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ DocumentCommand, IAutoEditStrategy, IDocument }

/**
 * Automatically applies several auto edit actions when a user enters or removes
 * an opening or closing bracket.
 */
class BracketAutoEditStrategy(prefStore: IPreferenceStore) extends IAutoEditStrategy {
  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    def isLineEndEmpty = {
      val lineInfo = document.getLineInformationOfOffset(command.offset)
      val str = document.get(command.offset, lineInfo.getLength() + lineInfo.getOffset() - command.offset)
      val (open, close) = (str.count(_ == '{'), str.count(_ == '}'))
      val hasUnmatchedClosingBracket = (open > 0 || close > 0) && open < close
      hasUnmatchedClosingBracket || str.trim.length == 0
    }

    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def addClosingBrace() {
      val isAutoClosingEnabled = prefStore.getBoolean(
          EditorPreferencePage.P_ENABLE_AUTO_CLOSING_BRACES)

      if (isAutoClosingEnabled || isLineEndEmpty) {
        command.text = "{}"
      }
      command.caretOffset = command.offset + 1
      command.shiftsCaret = false
    }

    def jumpOverClosingBrace() {
      if (ch(0, '}')) {
        command.text = ""
        command.caretOffset = command.offset + 1
      }
    }

    def removeClosingBrace() {
      if (ch(0, '{') && ch(1, '}')) {
        command.length = 2
      }
    }

    command.text match {
      case "{" => addClosingBrace()
      case "}" => jumpOverClosingBrace()
      case ""  => removeClosingBrace()
      case _   =>
    }
  }
}
package org.scalaide.ui.internal.editor.autoedits

import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument

/**
 * Automatically applies several auto edit actions when a user enters or removes
 * an opening or closing bracket.
 */
class BracketAutoEditStrategy(prefStore: IPreferenceStore) extends IAutoEditStrategy {

  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {

    /*
     * Checks if it is necessary to insert a closing brace. Normally this is
     * always the case with two exceptions:
     *
     * 1. The caret is positioned directly before non white space
     * 2. There are unmatched closing braces after the caret position.
     */
    def autoClosingRequired = {
      val lineInfo = document.getLineInformationOfOffset(command.offset)
      val lineAfterCaret = document.get(command.offset, lineInfo.getLength() + lineInfo.getOffset() - command.offset).toSeq

      if (lineAfterCaret.isEmpty) true
      else {
        val lineComplete = document.get(lineInfo.getOffset(), lineInfo.getLength()).toSeq
        val lineBeforeCaret = lineComplete.take(lineComplete.length - lineAfterCaret.length)

        val bracesTotal = lineComplete.count(_ == '}') - lineComplete.count(_ == '{')
        val bracesStart = lineComplete.takeWhile(_ != '{').count(_ == '}')
        val bracesEnd = lineComplete.reverse.takeWhile(_ != '}').count(_ == '{')
        val blacesRelevant = bracesTotal - bracesStart - bracesEnd

        val hasClosingBracket = lineAfterCaret.contains('}') && !lineAfterCaret.takeWhile(_ == '}').contains('{')
        val hasOpeningBracket = lineBeforeCaret.contains('{') && !lineBeforeCaret.reverse.takeWhile(_ == '{').contains('}')

        if (hasOpeningBracket && hasClosingBracket)
          blacesRelevant <= 0
        else
          lineAfterCaret(0) == ' ' || lineAfterCaret(0) == '\t'
      }
    }

    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def addClosing(pref: String, pair: String) = {
      val isAutoClosingEnabled = prefStore.getBoolean(pref)

      if (isAutoClosingEnabled) {
        command.text = pair
        command.caretOffset = command.offset + 1
        command.shiftsCaret = false
      }
    }

    def addClosingBrace() {
      val isAutoClosingEnabled = prefStore.getBoolean(
          EditorPreferencePage.P_ENABLE_AUTO_CLOSING_BRACES)
      val isSmartAutoClosingEnabled = prefStore.getBoolean(
          EditorPreferencePage.P_ENABLE_SMART_INSERTION_BRACES)

      if (isAutoClosingEnabled) {
        if (!isSmartAutoClosingEnabled || (isSmartAutoClosingEnabled && autoClosingRequired))
          command.text = "{}"
      }
      command.caretOffset = command.offset + 1
      command.shiftsCaret = false
    }

    def jumpOver(c: Char) {
      if (ch(0, c)) {
        command.text = ""
        command.caretOffset = command.offset + 1
      }
    }

    def removeClosingBrace() {
      if (ch(0, '{') && ch(1, '}')
          || ch(0, '(') && ch(1, ')')
          || ch(0, '[') && ch(1, ']')
          || ch(0, '<') && ch(1, '>')) {
        command.length = 2
      }
    }

    command.text match {
      case "{" => addClosingBrace()
      case c @ ("}" | ")" | "]" | ">") => jumpOver(c.head)
      case "(" => addClosing(EditorPreferencePage.P_ENABLE_AUTO_CLOSING_PARENS, "()")
      case "[" => addClosing(EditorPreferencePage.P_ENABLE_AUTO_CLOSING_SQUARE_BRACKETS, "[]")
      case "<" => addClosing(EditorPreferencePage.P_ENABLE_AUTO_CLOSING_ANGLE_BRACKETS, "<>")
      case ""  => removeClosingBrace()
      case _   =>
    }
  }
}

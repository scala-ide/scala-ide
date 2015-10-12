package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.scalaide.core.internal.statistics.Features
import org.scalaide.ui.internal.preferences.EditorPreferencePage

/**
 * Applies several auto edit actions to string and character literals.
 */
class LiteralAutoEditStrategy(prefStore: IPreferenceStore) extends IAutoEditStrategy {

  override def customizeDocumentCommand(document: IDocument, command: DocumentCommand): Unit = {

    val isAutoEscapeSignEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_ESCAPE_SIGN)
    val isAutoRemoveEscapedSignEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN)

    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def removeLiteral(): Unit = {
      def isEscapeSequence(i: Int) =
        """btnfr"'\""".contains(document.getChar(command.offset + i))

      if (ch(-1, '\\') && isEscapeSequence(0)) {
        command.length = 2
        command.offset -= 1
      } else if (ch(0, '\\') && isEscapeSequence(1)) {
        if (ch(1, ''')) {
          if (ch(2, ''')) {
            command.length = 2
          }
        } else
          command.length = 2
      } else if ((ch(0, '"') && ch(1, '"')) || (ch(0, ''') && ch(1, ''')))
        command.length = 2
    }

    def removeEscapedSign(): Unit = {
      if (ch(-1, '\\')) {
        Features.AutoRemoveEscapedSign.incUsageCounter()
        command.length = 2
        command.offset -= 1
      }
    }

    def jumpOverClosingLiteral(): Unit = {
      command.text = ""
      command.caretOffset = command.offset + 1
    }

    def handleClosingLiteral(): Unit = {
      val isCharFilled = if (ch(-1, ''')) ch(-2, '\\') else !ch(-1, '\\')

      if (ch(0, ''') && isCharFilled)
        jumpOverClosingLiteral()
    }

    def handleEscapeSign(): Unit = {
      Features.AutoEscapeBackslashes.incUsageCounter()
      if (!ch(-1, '\\')) {
        command.text = "\\\\"
      }
    }

    def handleClosingMultiLineLiteral(): Unit = {
      command.caretOffset = command.offset + 1
      command.text = command.text * 4
      command.shiftsCaret = false
    }

    def customizeLiteral(): Unit = {
      command.text match {
        case "\\" if isAutoEscapeSignEnabled      => handleEscapeSign()
        case "'"                                  => handleClosingLiteral()
        case "" if isAutoRemoveEscapedSignEnabled => removeEscapedSign()
        case _                                    =>
      }
    }

    def isEmptyLiteral =
      ((ch(0, ''') && ch(1, ''')) || (ch(0, '"') && ch(1, '"'))) && !ch(-1, '\\')

    def deleteEmptyLiteral(): Unit = {
      command.length = 2
    }

    def customizeChar(): Unit = {
      command.text match {
        case "" if isEmptyLiteral                 => deleteEmptyLiteral()
        case "" if isAutoRemoveEscapedSignEnabled => removeLiteral()
        case _                                    =>
      }
    }

    def customizeMultiLineLiteral(): Unit = {
      command.text match {
        case "\"" => handleClosingMultiLineLiteral()
        case _    =>
      }
    }

    if (command.length > 1) {
      /*
       * The current auto edit strategy is not able to handle changes that added or
       * removed more than a single sign, thus further calculations are aborted to
       * avoid wrong behavior.
       * This is only a temporary solution - in the future the needed behavior
       * should be implemented.
       */
      return
    }

    val isCharacterLiteral = ch(-1, ''') && ch(0, ''')
    val isMultiLineStringLiteral = ch(-2, '"') && ch(-1, '"')

    /*
     * Normally, character literals should be handled by its own component. But
     * because two consecutive apostrophes '' are not valid Scala syntax, the
     * partitioner doesn't move them to an own partition, hence the same component
     * is called when a sign is added to such a character literal.
     *
     * If in future the partitioning strategy for character literals changes
     * in this case, the code called my `cutomizeLiteral` should be moved in its
     * own component (and this new component also should be configured in
     * `ScalaSourceViewerConfiguration`) otherwise the corresponding behavior is
     * not called
     */
    if (isMultiLineStringLiteral)
      customizeMultiLineLiteral()
    else if (isCharacterLiteral)
      customizeLiteral()
    else
      customizeChar()
  }
}

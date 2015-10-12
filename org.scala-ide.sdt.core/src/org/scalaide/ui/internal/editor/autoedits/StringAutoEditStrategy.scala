package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities
import org.scalaide.core.internal.statistics.Features
import org.scalaide.ui.internal.preferences.EditorPreferencePage

/**
 * Applies several auto edit actions if one adds ore removes a sign inside of
 * a string.
 */
class StringAutoEditStrategy(partitioning: String, prefStore: IPreferenceStore) extends IAutoEditStrategy {

  override def customizeDocumentCommand(document: IDocument, command: DocumentCommand): Unit = {

    val isAutoEscapeLiteralEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_ESCAPE_LITERALS)
    val isAutoEscapeSignEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_ESCAPE_SIGN)
    val isAutoRemoveEscapedSignEnabled = prefStore.getBoolean(
        EditorPreferencePage.P_ENABLE_AUTO_REMOVE_ESCAPED_SIGN)

    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def isStringTerminated = {
      val partition = TextUtilities.getPartition(document, partitioning, command.offset, true)
      val endPos = partition.getOffset() + partition.getLength() - 1
      val isStringTerminated = document.getChar(endPos) == '"'
      val isTerminationEscaped = document.getChar(endPos - 1) == '\\'
      isStringTerminated && !isTerminationEscaped
    }

    def removeEscapedSign(): Unit = {
      def isEscapeSequence(i: Int) =
        """btnfr"'\""".contains(document.getChar(command.offset + i))

      if (ch(-1, '\\') && isEscapeSequence(0)) {
        Features.AutoRemoveEscapedSign.incUsageCounter()
        command.length = 2
        command.offset -= 1
      } else if (ch(0, '\\') && isEscapeSequence(1)) {
        if (ch(1, '"')) {
          if (isStringTerminated) {
            Features.AutoRemoveEscapedSign.incUsageCounter()
            command.length = 2
          }
        } else {
          Features.AutoRemoveEscapedSign.incUsageCounter()
          command.length = 2
        }
      }
    }

    def jumpOverClosingLiteral(): Unit = {
      command.text = ""
      command.caretOffset = command.offset + 1
    }

    def handleClosingLiteral(): Unit = {
      if (ch(0, '"') && ch(-1, '"') && !ch(-2, '\\'))
        jumpOverClosingLiteral()
      else if (isAutoEscapeLiteralEnabled && isStringTerminated) {
        Features.AutoEscapeLiterals.incUsageCounter()
        if (ch(-1, '\\')) {
          if (ch(-2, '\\'))
            command.text = "\\\""
        }
        else command.text = "\\\""
      }
    }

    def handleEscapeSign(): Unit = {
      Features.AutoEscapeBackslashes.incUsageCounter()
      if (ch(-1, '\\')) {
        if (ch(-2, '\\'))
          command.text = "\\\\"
      }
      else command.text = "\\\\"
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

    command.text match {
      case "\\" if isAutoEscapeSignEnabled      => handleEscapeSign()
      case "\""                                 => handleClosingLiteral()
      case "" if isAutoRemoveEscapedSignEnabled => removeEscapedSign()
      case _                                    =>
    }
  }

}

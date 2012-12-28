package scala.tools.eclipse.ui

import org.eclipse.jface.text.{ DocumentCommand, IAutoEditStrategy, IDocument }

/**
 * Applies several auto edit actions to string and character literals.
 */
class LiteralAutoEditStrategy extends IAutoEditStrategy {

  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def addClosingLiteral() {
      if (ch(-1, '"') || ch(-1, ''') || ch(0, '"') || ch(0, ''')) {
        return
      }
      command.caretOffset = command.offset + 1
      command.text += command.text
      command.shiftsCaret = false
    }

    def removeLiteral() {
      if ((ch(0, '"') && ch(1, '"')) || (ch(0, ''') && ch(1, '''))) {
        command.length = 2
      }
    }

    def removeEscapedSign() {
      if (ch(-1, '\\') && (ch(0, ''') || ch(0, '\\'))) {
        command.length = 2
        command.offset -= 1
      }
    }

    def jumpOverClosingLiteral() {
      command.text = ""
      command.caretOffset = command.offset + 1
    }

    def handleClosingLiteral() {
      val isCharFilled = if (ch(-1, ''')) ch(-2, '\\') else !ch(-1, '\\')

      if (ch(0, ''') && isCharFilled)
        jumpOverClosingLiteral()
      else if (!ch(-1, '\\') && ch(-2, '''))
        command.text = "\\'"
    }

    def handleEscapeSign() {
      if (!ch(-1, '\\')) {
        command.text = "\\\\"
      }
    }

    def customizeLiteral() {
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
        case "\\" => handleEscapeSign()
        case "'"  => handleClosingLiteral()
        case ""   => removeEscapedSign()
        case _    =>
      }
    }

    def customizeChar() {
      command.text match {
        case "\"" | "'" => addClosingLiteral()
        case ""         => removeLiteral()
        case _          =>
      }
    }

    val isCharacterLiteral = (ch(-1, ''') || ch(-1, '\\')) && (ch(0, ''') || ch(0, '\\'))

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
    if (isCharacterLiteral)
      customizeLiteral()
    else
      customizeChar()
  }
}

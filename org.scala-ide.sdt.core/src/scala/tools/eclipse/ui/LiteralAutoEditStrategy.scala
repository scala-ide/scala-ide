package scala.tools.eclipse.ui

import org.eclipse.jface.text.{ DocumentCommand, IAutoEditStrategy, IDocument }

/**
 * Applies several auto edit actions to string literals.
 */
class LiteralAutoEditStrategy extends IAutoEditStrategy {

  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def addClosingLiteral() {
      if (ch(-1, '"') || ch(0, '"')) {
        return
      }
      command.caretOffset = command.offset + 1
      command.text += command.text
      command.shiftsCaret = false
    }

    def removeClosingLiteral() {
      if (ch(0, '"') && ch(1, '"')) {
        command.length = 2
      }
    }

    command.text match {
      case "\"" => addClosingLiteral()
      case ""   => removeClosingLiteral()
      case _    =>
    }
  }
}
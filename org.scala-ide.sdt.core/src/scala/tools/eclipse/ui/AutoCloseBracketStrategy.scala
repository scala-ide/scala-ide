package scala.tools.eclipse.ui

import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument

/** Automatically adds a matching closing bracket whenever the user enters a left bracket. */
class AutoCloseBracketStrategy extends IAutoEditStrategy {
  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    if (command.text == "{") {
      command.text = command.text + "}"
      command.caretOffset = command.offset + 1
      command.shiftsCaret = false
    }
  }
}
package scala.tools.eclipse.ui

import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument

/** Automatically adds a matching closing bracket whenever the user enters a left bracket. */
class AutoCloseBracketStrategy extends IAutoEditStrategy {
  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    command.text match {
      case "{" => // add a closing brace
        command.text = "{}"
        command.caretOffset = command.offset + 1
        command.shiftsCaret = false
      case "}" => // jump over closing brace
        if (document.get(command.offset, 1) == "}") {
          command.text = ""
          command.caretOffset = command.offset + 1
        }
      case "" => // remove closing brace
        if (command.length == 1 && document.get(command.offset, 2) == "{}") {
          command.length = 2
        }
      case _ =>
    }

  }
}
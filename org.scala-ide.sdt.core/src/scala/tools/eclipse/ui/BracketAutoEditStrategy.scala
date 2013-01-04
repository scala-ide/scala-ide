package scala.tools.eclipse.ui

import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument

/**
 * Automatically applies several auto edit actions when a user enters or removes
 * an opening or closing bracket.
 */
class BracketAutoEditStrategy extends IAutoEditStrategy {
    def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {
    command.text match {
      case "{" => // add a closing brace
        command.text = "{}"
        command.caretOffset = command.offset + 1
        command.shiftsCaret = false
      case "}" => // jump over closing brace
        if (document.getLength > command.offset && document.get(command.offset, 1) == "}") {
          command.text = ""
          command.caretOffset = command.offset + 1
        }
      case "" => // remove closing brace
        if (document.getLength > command.offset + 1 && command.length == 1 && document.get(command.offset, 2) == "{}") {
          command.length = 2
        }
      case _ =>
    }

  }
}
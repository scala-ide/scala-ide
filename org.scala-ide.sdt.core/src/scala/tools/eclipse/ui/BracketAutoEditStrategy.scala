package scala.tools.eclipse.ui

import org.eclipse.jface.text.{ DocumentCommand, IAutoEditStrategy, IDocument }
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.properties.EditorPreferencePage
import org.eclipse.jface.preference.IPreferenceStore

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

    command.text match {
      case "{" => // add a closing brace
        val isAutoClosingEnabled = prefStore.getBoolean(
            EditorPreferencePage.P_ENABLE_AUTO_CLOSING_BRACES)

        if (isAutoClosingEnabled || isLineEndEmpty) {
          command.text = "{}"
        }
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
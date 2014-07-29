package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextUtilities

class MultiLineStringAutoEditStrategy(prefStore: IPreferenceStore) extends IAutoEditStrategy {

  def customizeDocumentCommand(document: IDocument, command: DocumentCommand) {

    def ch(i: Int, c: Char) = {
      val o = command.offset + i
      o >= 0 && o < document.getLength && document.getChar(o) == c
    }

    def removeClosingLiteral() {
      val isLiteralEmpty = -2 to 3 forall (ch(_, '"'))

      if (isLiteralEmpty) {
        command.length = 4
        command.offset -= 1
      }
    }

    def jumpOverClosingLiteral() {
      command.text = ""
      command.caretOffset = command.offset + 1
    }

    def handleClosingLiteral() {
      val cs = -2 to 2 map (ch(_, '"'))
      val existThreeConsecutiveApostrophes = cs sliding 3 exists (_ forall identity)
      if (existThreeConsecutiveApostrophes)
        jumpOverClosingLiteral()
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
      case "\"" => handleClosingLiteral()
      case ""   => removeClosingLiteral()
      case _    =>
    }
  }
}

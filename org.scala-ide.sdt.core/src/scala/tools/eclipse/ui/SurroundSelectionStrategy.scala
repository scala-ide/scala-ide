package scala.tools.eclipse.ui

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.VerifyEvent

class SurroundSelectionStrategy(sourceViewer: ISourceViewer) extends VerifyKeyListener {
  val activeChars = Map(
    '(' -> ')',
    '{' -> '}',
    '"' -> '"',
    '[' -> ']')

  /** Automatically surround the current selection with the corresponding
   *  character, if it is a parenthesis/brace of quotes.
   *  
   *  Since it gets a chance to see all characters, it also suppresses the
   *  automatically generated closing angle bracket that the Java editor
   *  always appends, leading to <> in the code.
   */
  override def verifyKey(event: VerifyEvent) {
    val selection = sourceViewer.getSelectedRange
    val doc = sourceViewer.getDocument
    val chr = event.character

    if (selection.y > 0 && activeChars.isDefinedAt(chr)) {
      val text = doc.get(selection.x, selection.y)
      doc.replace(selection.x, selection.y, chr + text + activeChars(chr))
      // stop the Java editor from adding a closing bracket as well
      event.doit = false
    } else if (chr == '<') {
      // the Java editor usually inserts a closing angle bracket (Java Generics)
      // we suppress it here.
      doc.replace(selection.x, 0, "<")
      sourceViewer.setSelectedRange(selection.x + 1, 0)
      event.doit = false
    }
  }
}
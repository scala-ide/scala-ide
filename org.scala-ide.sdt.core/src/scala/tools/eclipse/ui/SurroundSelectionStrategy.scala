package scala.tools.eclipse.ui

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.jface.util.IPropertyChangeListener
import scala.tools.eclipse.properties.EditorPreferencePage._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.util.PropertyChangeEvent

class SurroundSelectionStrategy(sourceViewer: ISourceViewer) extends VerifyKeyListener with IPropertyChangeListener {
  @volatile
  private var activeChars = getConfiguredActiveChars

  ScalaPlugin.prefStore.addPropertyChangeListener(this)

  private lazy val optionToMapping = Map(
    P_ENABLE_SMART_PARENS -> ('(', ')'),
    P_ENABLE_SMART_BRACES -> ('{', '}'),
    P_ENABLE_SMART_QUOTES -> ('"', '"'),
    P_ENABLE_SMART_BRACKETS -> ('[', ']'))

  /** Automatically surround the current selection with the corresponding
   *  character, if it is defined in the `activeChars` map.
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

  /** Return a map of characters that have been enabled to act as active chars.
   *
   *  @note For each pair `(c1, c2)` in the map, when pressing c1 and a selection is
   *        active, it will surround it with c1,c2 instead of replacing the selection.
   */
  private def getConfiguredActiveChars: Map[Char, Char] = {
    val store = ScalaPlugin.prefStore
    for ((option, mapping) <- optionToMapping if store.getBoolean(option)) yield mapping
  }

  def propertyChange(event: PropertyChangeEvent) {
    activeChars = getConfiguredActiveChars
  }
}

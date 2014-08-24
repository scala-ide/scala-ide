package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.jface.util.IPropertyChangeListener
import org.scalaide.ui.internal.preferences.EditorPreferencePage._
import org.scalaide.core.IScalaPlugin
import org.eclipse.jface.util.PropertyChangeEvent

class SurroundSelectionStrategy(sourceViewer: ISourceViewer) extends VerifyKeyListener with IPropertyChangeListener {
  @volatile
  private var activeChars = getConfiguredActiveChars

  IScalaPlugin().getPreferenceStore().addPropertyChangeListener(this)

  private lazy val optionToMapping = Map(
    P_ENABLE_SMART_PARENS -> (('(', ')')),
    P_ENABLE_SMART_BRACES -> (('{', '}')),
    P_ENABLE_SMART_QUOTES -> (('"', '"')),
    P_ENABLE_SMART_BRACKETS -> (('[', ']')))

  /** Automatically surround the current selection with the corresponding
   *  character, if it is defined in the `activeChars` map.
   */
  override def verifyKey(event: VerifyEvent) {
    val selection = sourceViewer.getSelectedRange
    val doc = sourceViewer.getDocument
    val chr = event.character

    if (selection.y > 0 && activeChars.isDefinedAt(chr)) {
      val text = doc.get(selection.x, selection.y)
      doc.replace(selection.x, selection.y, chr + text + activeChars(chr))
      event.doit = false
    }
  }

  /** Return a map of characters that have been enabled to act as active chars.
   *
   *  @note For each pair `(c1, c2)` in the map, when pressing c1 and a selection is
   *        active, it will surround it with c1,c2 instead of replacing the selection.
   */
  private def getConfiguredActiveChars: Map[Char, Char] = {
    val store = IScalaPlugin().getPreferenceStore()
    for ((option, mapping) <- optionToMapping if store.getBoolean(option)) yield mapping
  }

  override def propertyChange(event: PropertyChangeEvent) {
    activeChars = getConfiguredActiveChars
  }
}

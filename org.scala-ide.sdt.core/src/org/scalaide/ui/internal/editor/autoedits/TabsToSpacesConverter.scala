package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultLineTracker
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.{TabsToSpacesConverter => ETabsToSpacesConverter}
import org.scalaide.core.internal.formatter.FormatterPreferences._

import scalariform.formatter.preferences._

/**
 * Auto edit strategy that converts tabs into spaces. Before any conversion is
 * done, the Scala formatter preferences are considered - no conversion happens
 * if tabs are configured to be used.
 */
class TabsToSpacesConverter(prefStore: IPreferenceStore) extends ETabsToSpacesConverter {

  setLineTracker(new DefaultLineTracker)

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand): Unit = {
    val useTabs = prefStore.getBoolean(IndentWithTabs.eclipseKey)

    if (!useTabs) {
      val tabWidth = prefStore.getInt(IndentSpaces.eclipseKey)
      setNumberOfSpacesPerTab(tabWidth)
      super.customizeDocumentCommand(doc, cmd)
    }
  }

}

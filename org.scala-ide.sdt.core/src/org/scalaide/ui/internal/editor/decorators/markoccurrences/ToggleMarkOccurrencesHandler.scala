package org.scalaide.ui.internal.editor.decorators.markoccurrences

import org.scalaide.ui.internal.actions.AbstractToggleHandler
import org.scalaide.ui.internal.preferences.EditorPreferencePage

/**
 * Handler to toggle the Occurrences (shortcut to avoid open Preferences,...)
 */
class ToggleMarkOccurrencesHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.toggleMarkOccurences", EditorPreferencePage.P_ENABLE_MARK_OCCURRENCES)

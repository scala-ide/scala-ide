package scala.tools.eclipse.markoccurrences

import scala.tools.eclipse.properties.ImplicitsPreferencePage
import scala.tools.eclipse.ui.AbstractToggleHandler
import scala.tools.eclipse.properties.EditorPreferencePage

/**
 * Handler to toggle the Occurrences (shortcut to avoid open Preferences,...)
 * 
 * @see scala.tools.eclipse.ui.AbstractToggleHandler
 */

class ToggleMarkOccurrencesHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.toggleMarkOccurences", EditorPreferencePage.P_ENABLE_MARK_OCCURRENCES)
